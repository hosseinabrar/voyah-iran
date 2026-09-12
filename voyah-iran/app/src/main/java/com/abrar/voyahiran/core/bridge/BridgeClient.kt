package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

object BridgeClient {
    @Volatile private var tokenFile: File? = null
    fun configure(files: File) { tokenFile = File(files, "bridge.token") }

    fun run(arguments: List<String>, timeoutSeconds: Long): CommandResult {
        if (BridgePolicy.resolve(arguments) == null) return CommandResult(126, "Operation is not permitted by bridge policy")
        val request = UUID.randomUUID().toString()
        var sent = false
        return try {
            val token = tokenFile?.readText()?.trim() ?: error("Bridge is not provisioned")
            check(token.matches(Regex("[a-f0-9]{64}"))) { "Invalid bridge credentials" }
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", BridgePolicy.PORT), 2000)
                socket.soTimeout = ((timeoutSeconds.coerceIn(1, 25) + 5) * 1000).toInt()
                val output = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                output.writeInt(BridgePolicy.MAGIC)
                output.writeUTF(token)
                output.writeUTF(request)
                output.writeInt(timeoutSeconds.coerceIn(1, 25).toInt())
                output.writeInt(arguments.size)
                arguments.forEach(output::writeUTF)
                sent = true
                output.flush()
                check(input.readInt() == BridgePolicy.MAGIC && input.readUTF() == request) { "Bridge response mismatch" }
                val result = CommandResult(input.readInt(), input.readUTF(), input.readBoolean())
                AppLog.info("Bridge", "request=$request exit=${result.exitCode} timedOut=${result.timedOut}")
                result
            }
        } catch (error: Exception) {
            AppLog.error("Bridge", "request=$request failed sent=$sent no-retry", error)
            // A lost reply may follow a completed write. Never retry automatically.
            CommandResult(if (sent) 124 else 127, if (sent) "Bridge response lost; operation outcome unknown" else "Bridge unavailable; run the Windows installer", sent)
        }
    }
}
