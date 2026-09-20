package com.abrar.voyahiran.feature.diagnostics

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.Collections

/** Experimental ISO 13400 client. No address sweep, session escalation or security unlock. */
internal object DtcCodec {
    fun u16(b: ByteArray, offset: Int) = ((b[offset].toInt() and 255) shl 8) or (b[offset + 1].toInt() and 255)
    fun address(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
    fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 255) }
    fun frame(type: Int, payload: ByteArray, version: Int = 2): ByteArray =
        java.io.ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeByte(version); out.writeByte(version xor 255)
                out.writeShort(type); out.writeInt(payload.size); out.write(payload)
            }
        }.toByteArray()
    fun parse(response: ByteArray): List<Dtc> {
        require(response.size >= 3 && response[0] == 0x59.toByte() && response[1] == 2.toByte()) { "Invalid ReadDTC response" }
        require((response.size - 3) % 4 == 0) { "Truncated DTC record" }
        val available = response[2].toInt() and 255
        require(available != 0) { "ECU reports no supported DTC status bits" }
        return (3 until response.size step 4).map { offset ->
            Dtc(response.sliceArray(offset..offset + 2).joinToString("") { "%02X".format(it.toInt() and 255) }, response[offset + 3].toInt() and available)
        }.filter { it.status != 0 }
    }
}
internal data class Dtc(val code: String, val status: Int)
internal data class DoipEntity(val host: String, val local: String, val logical: Int, val version: Int) {
    val label get() = "$host / ECU 0x%04X".format(logical)
}
internal data class DtcReading(val entity: DoipEntity, val faults: List<Dtc>)
internal data class DiagnosticResult(val readings: List<DtcReading>, val failures: List<String>)

internal class DoipDiagnostics(private val log: (String) -> Unit, private val port: Int = 13400, private val checkActive: () -> Unit = {}) {
    private val tester = 0x0e00
    fun discover(localOnly: Boolean): List<DoipEntity> {
        val found = linkedSetOf<DoipEntity>()
        val routes = mutableListOf("127.0.0.1" to "127.0.0.1")
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        interfaces.forEach { nic ->
            log("interface=${nic.name} up=${nic.isUp} addresses=${nic.interfaceAddresses.map { it.address.hostAddress }}")
            // Automotive Ethernet/USB only; do not probe unrelated Wi-Fi or mobile networks.
            if (!localOnly && nic.isUp && !nic.isLoopback && (nic.name.startsWith("eth") || nic.name.startsWith("usb") || nic.name.startsWith("rndis"))) {
                nic.interfaceAddresses.filter { it.address is Inet4Address && it.broadcast != null }.forEach {
                    routes += it.address.hostAddress!! to it.broadcast.hostAddress!!
                }
            }
        }
        routes.distinct().take(8).forEach { (local, target) ->
            try {
                DatagramSocket(InetSocketAddress(InetAddress.getByName(local), 0)).use { socket ->
                    socket.broadcast = true
                    val request = DtcCodec.frame(1, byteArrayOf(), 0xff)
                    log("discovery local=$local destination=$target:$port")
                    socket.send(DatagramPacket(request, request.size, InetAddress.getByName(target), port))
                    val deadline = System.nanoTime() + 1_500_000_000L
                    while (System.nanoTime() < deadline) {
                        socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)
                        val packet = DatagramPacket(ByteArray(2048), 2048)
                        try { socket.receive(packet) } catch (_: SocketTimeoutException) { break }
                        val b = packet.data.copyOf(packet.length)
                        if (b.size < 40 || packet.port != port) continue
                        val version = b[0].toInt() and 255
                        if (version !in 2..3 || (b[1].toInt() and 255) != (version xor 255) || DtcCodec.u16(b, 2) != 4) continue
                        val length = java.nio.ByteBuffer.wrap(b, 4, 4).int
                        if (length !in 32..33 || b.size != length + 8) continue
                        val logical = DtcCodec.u16(b, 25)
                        if (logical !in 1..0x0dff && logical !in 0x1000..0x7fff) continue
                        val entity = DoipEntity(packet.address.hostAddress!!, local, logical, version)
                        found += entity
                        log("identified ${entity.label} version=$version (VIN omitted)")
                    }
                }
            } catch (e: Exception) { log("discovery failed $local: ${e.javaClass.simpleName}: ${e.message}") }
        }
        return found.distinctBy { it.host to it.logical }.take(16)
    }
    fun read(localOnly: Boolean): DiagnosticResult {
        val good = mutableListOf<DtcReading>(); val failed = mutableListOf<String>()
        val entities = discover(localOnly)
        if (entities.isEmpty()) failed += "هیچ واحد DoIP پاسخ نداد؛ این نتیجه به معنی نبود خطا نیست."
        entities.forEach { entity ->
            try { good += readEntity(entity) }
            catch (e: Exception) { log("read failed ${entity.label}: ${e.message}"); failed += "${entity.label}: ${e.message}" }
        }
        return DiagnosticResult(good, failed)
    }
    fun readEntity(entity: DoipEntity): DtcReading {
        val faults = DtcCodec.parse(exchange(entity, byteArrayOf(0x19, 2, 0xff.toByte())))
        faults.forEach { log("DTC ${entity.label} code=${it.code} status=%02X".format(it.status)) }
        return DtcReading(entity, faults)
    }
    /** Called once per confirmed target; never automatically retry a clear request. */
    fun clear(entity: DoipEntity) {
        val response = exchange(entity, byteArrayOf(0x14, 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        require(response.contentEquals(byteArrayOf(0x54))) { "Clear not confirmed: ${DtcCodec.hex(response)}" }
    }
    private fun exchange(entity: DoipEntity, request: ByteArray): ByteArray {
        return try {
            exchangeOnce(entity, request, extendedActivation = false)
        } catch (_: ActivationLengthRejected) {
            // Generic header NACK 04 BEFORE UDS: some entities require the optional
            // four-byte OEM field. Reconnect because this NACK closes the TCP socket.
            // Never retry after sending a diagnostic request (especially ClearDTC).
            log("${entity.label}: 7-byte activation rejected for length; reconnecting once with 11-byte activation (OEM field=0)")
            exchangeOnce(entity, request, extendedActivation = true)
        }
    }
    private class ActivationLengthRejected : IllegalStateException("DoIP activation rejected: invalid payload length (04)")

    private fun exchangeOnce(entity: DoipEntity, request: ByteArray, extendedActivation: Boolean): ByteArray {
        checkActive()
        Socket().use { socket ->
            socket.bind(InetSocketAddress(InetAddress.getByName(entity.local), 0))
            socket.connect(InetSocketAddress(entity.host, port), 2000)
            val input = DataInputStream(socket.getInputStream()); val out = socket.getOutputStream()
            val deadline = System.nanoTime() + 8_000_000_000L
            fun send(type: Int, payload: ByteArray) {
                checkActive()
                log("TX ${entity.label} type=%04X data=${DtcCodec.hex(payload)}".format(type))
                out.write(DtcCodec.frame(type, payload, entity.version)); out.flush()
            }
            fun receive(): Pair<Int, ByteArray> {
                checkActive()
                val remaining = (deadline - System.nanoTime()) / 1_000_000
                require(remaining > 0) { "Diagnostic timeout; outcome unconfirmed" }
                socket.soTimeout = remaining.toInt().coerceAtMost(2000).coerceAtLeast(1)
                val version = input.readUnsignedByte(); val inverse = input.readUnsignedByte()
                require(version == entity.version && inverse == (version xor 255)) { "Invalid DoIP header" }
                val type = input.readUnsignedShort(); val length = input.readInt()
                require(length in 0..65536) { "Invalid DoIP length $length" }
                val payload = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    checkActive()
                    val budget = (deadline - System.nanoTime()) / 1_000_000
                    require(budget > 0) { "Diagnostic payload timeout" }
                    socket.soTimeout = budget.toInt().coerceIn(1, 2000)
                    val count = input.read(payload, offset, length - offset)
                    require(count > 0) { "Truncated DoIP payload" }
                    offset += count
                }
                log("RX ${entity.label} type=%04X bytes=$length data=${DtcCodec.hex(payload.take(512).toByteArray())}".format(type))
                return type to payload
            }
            val activation = DtcCodec.address(tester) + ByteArray(if (extendedActivation) 9 else 5)
            log("routing activation ${entity.label} payloadLength=${activation.size} tester=0E00 activationType=00")
            send(5, activation)
            var activated = false
            while (!activated) {
                val (type, p) = receive()
                when (type) {
                    6 -> { require(p.size >= 9 && DtcCodec.u16(p, 0) == tester && DtcCodec.u16(p, 2) == entity.logical && p[4] == 0x10.toByte()) { "Routing activation rejected: ${DtcCodec.hex(p)}" }; activated = true }
                    7 -> send(8, DtcCodec.address(tester))
                    0 -> {
                        if (p.contentEquals(byteArrayOf(4)) && !extendedActivation) throw ActivationLengthRejected()
                        error("DoIP activation header NACK ${DtcCodec.hex(p)}${if (p.contentEquals(byteArrayOf(4))) " (invalid payload length; both activation formats rejected)" else ""}")
                    }
                    else -> error("Unexpected activation response $type")
                }
            }
            send(0x8001, DtcCodec.address(tester) + DtcCodec.address(entity.logical) + request)
            while (true) {
                val (type, p) = receive()
                when (type) {
                    7 -> send(8, DtcCodec.address(tester))
                    0, 0x8003 -> error("DoIP rejected: ${DtcCodec.hex(p)}")
                    0x8002 -> require(p.size >= 5 && DtcCodec.u16(p, 0) == entity.logical && DtcCodec.u16(p, 2) == tester && p[4] == 0.toByte()) { "Invalid diagnostic ACK" }
                    0x8001 -> {
                        require(p.size >= 5 && DtcCodec.u16(p, 0) == entity.logical && DtcCodec.u16(p, 2) == tester) { "Unexpected response address" }
                        val uds = p.copyOfRange(4, p.size)
                        if (uds[0] == 0x7f.toByte()) {
                            require(uds.size == 3 && uds[1] == request[0]) { "Invalid negative response" }
                            if (uds[2] == 0x78.toByte()) continue
                            error("UDS NRC=0x%02X (service 0x%02X)".format(uds[2].toInt() and 255, request[0].toInt() and 255))
                        }
                        require((uds[0].toInt() and 255) == (request[0].toInt() and 255) + 0x40) { "Unrelated UDS response" }
                        return uds
                    }
                    else -> error("Unexpected DoIP message $type")
                }
            }
        }
    }
}
