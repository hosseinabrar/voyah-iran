package com.abrar.voyahdowtest

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Root-authorized diagnostic only. Credentials never appear in logs or persistent probe storage. */
internal object AmbientTestClient {
    fun send(start: Boolean): String {
        val process = ProcessBuilder("su", "0", "cat",
            "/data/user/0/com.abrar.voyahiran/files/bridge.token").start()
        val token = try {
            check(process.waitFor(2, TimeUnit.SECONDS)) { "Root access timed out" }
            check(process.exitValue() == 0) { "Root access / installed bridge required" }
            process.inputStream.bufferedReader().readText().trim().also {
                check(it.matches(Regex("[a-f0-9]{64}"))) { "Invalid bridge credential" }
            }
        } finally {
            process.destroy(); process.inputStream.close(); process.errorStream.close(); process.outputStream.close()
        }
        return Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 38743), 2000)
            socket.soTimeout = 3000
            val id = UUID.randomUUID().toString()
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            output.writeInt(0x56495231); output.writeUTF(token); output.writeUTF(id)
            output.writeInt(2); output.writeInt(2)
            output.writeUTF("dow-light"); output.writeUTF(if (start) "test-start" else "test-stop")
            output.flush()
            check(input.readInt() == 0x56495231 && input.readUTF() == id) { "Bridge response mismatch" }
            val exit = input.readInt(); val message = input.readUTF(); val timeout = input.readBoolean()
            check(exit == 0 && !timeout) { message }
            message
        }
    }
}
