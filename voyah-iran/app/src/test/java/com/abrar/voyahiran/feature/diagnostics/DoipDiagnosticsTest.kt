package com.abrar.voyahiran.feature.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DoipDiagnosticsTest {
    @Test fun parsesRealStatusAndRejectsTruncation() {
        val result = DtcCodec.parse(byteArrayOf(0x59, 2, 0xff.toByte(), 0x12, 0x34, 0x56, 0x89.toByte()))
        assertEquals(listOf(Dtc("123456", 0x89)), result)
        assertThrows(IllegalArgumentException::class.java) { DtcCodec.parse(byteArrayOf(0x59, 2, 0xff.toByte(), 1)) }
        assertThrows(IllegalArgumentException::class.java) { DtcCodec.parse(byteArrayOf(0x7f, 0x19, 0x11)) }
        assertThrows(IllegalArgumentException::class.java) { DtcCodec.parse(byteArrayOf(0x59, 2, 0)) }
    }
    @Test fun emptyPositiveResponseIsNotTransportFailure() {
        assertTrue(DtcCodec.parse(byteArrayOf(0x59, 2, 0xff.toByte())).isEmpty())
    }
    @Test fun ignoresUnsupportedStatusBits() {
        assertTrue(DtcCodec.parse(byteArrayOf(0x59, 2, 1, 0x12, 0x34, 0x56, 8)).isEmpty())
    }
    @Test fun readsThroughRoutingActivationAndPending() {
        withServer(byteArrayOf(0x19, 2, 0xff.toByte()), byteArrayOf(0x59, 2, 0xff.toByte(), 1, 2, 3, 9), pending = true) { client, entity ->
            assertEquals(listOf(Dtc("010203", 9)), client.readEntity(entity).faults)
        }
    }
    @Test fun clearRequiresPositiveUdsConfirmation() {
        withServer(byteArrayOf(0x14, -1, -1, -1), byteArrayOf(0x54)) { client, entity -> client.clear(entity) }
        withServer(byteArrayOf(0x14, -1, -1, -1), byteArrayOf(0x7f, 0x14, 0x22)) { client, entity ->
            assertThrows(IllegalStateException::class.java) { client.clear(entity) }
        }
    }
    @Test fun rejectsMisdirectedResponse() {
        withServer(byteArrayOf(0x19, 2, -1), byteArrayOf(0x59, 2, -1), wrongAddress = true) { client, entity ->
            assertThrows(IllegalArgumentException::class.java) { client.readEntity(entity) }
        }
    }
    private fun withServer(request: ByteArray, response: ByteArray, pending: Boolean = false, wrongAddress: Boolean = false, block: (DoipDiagnostics, DoipEntity) -> Unit) {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val pool = Executors.newSingleThreadExecutor()
            try {
                val worker = pool.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = DataInputStream(socket.getInputStream())
                        fun receive(type: Int): ByteArray {
                            assertEquals(2, input.readUnsignedByte()); assertEquals(253, input.readUnsignedByte())
                            assertEquals(type, input.readUnsignedShort())
                            return ByteArray(input.readInt()).also { input.readFully(it) }
                        }
                        fun send(type: Int, bytes: ByteArray) { socket.getOutputStream().write(DtcCodec.frame(type, bytes)) }
                        assertArrayEquals(byteArrayOf(0x0e, 0, 0, 0, 0, 0, 0), receive(5))
                        send(6, byteArrayOf(0x0e, 0, 0x10, 1, 0x10, 0, 0, 0, 0))
                        assertArrayEquals(byteArrayOf(0x0e, 0, 0x10, 1) + request, receive(0x8001))
                        send(0x8002, byteArrayOf(0x10, 1, 0x0e, 0, 0))
                        if (pending) send(0x8001, byteArrayOf(0x10, 1, 0x0e, 0, 0x7f, request[0], 0x78))
                        send(0x8001, byteArrayOf(0x10, if (wrongAddress) 2 else 1, 0x0e, 0) + response)
                    }
                }
                block(DoipDiagnostics({}, server.localPort), DoipEntity("127.0.0.1", "127.0.0.1", 0x1001, 2))
                worker.get(6, TimeUnit.SECONDS)
            } finally { pool.shutdownNow() }
        }
    }
}
