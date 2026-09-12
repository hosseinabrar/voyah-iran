package com.abrar.voyahiran.core.bridge

import android.os.Process
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Started by init using app_process, not an exported Android component. */
object BridgeServer {
    private val roa = RoaHookManager()
    @JvmStatic fun main(args: Array<String>) {
        check(Process.myUid() == 0) { "Bridge must be started by the installer/init as root" }
        val token = File("/data/local/tmp/voyahiran-bridge/bridge.token").readText().trim()
        check(token.matches(Regex("[a-f0-9]{64}")))
        val runner = ProcessCommandRunner(useBridge = false)
        roa.start()
        val execution = java.util.concurrent.locks.ReentrantLock()
        val pool = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(4))
        ServerSocket(BridgePolicy.PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            AppLog.info("BridgeServer", "ready protocol=1 uid=${Process.myUid()} port=${BridgePolicy.PORT}")
            while (true) {
                val socket = server.accept()
                try { pool.execute { handle(socket, token, runner, execution) } }
                catch (_: java.util.concurrent.RejectedExecutionException) { socket.close() }
            }
        }
    }

    private fun handle(socket: Socket, token: String, runner: ProcessCommandRunner, execution: java.util.concurrent.locks.ReentrantLock) {
        socket.use {
            try {
                socket.soTimeout = 3000
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val parsed = BridgeProtocol.read(input, token)
                val request = parsed.id
                val command = BridgePolicy.resolve(parsed.arguments)
                val result = if (command == null) CommandResult(126, "Operation denied")
                else if (!execution.tryLock()) CommandResult(75, "Bridge busy; no command executed")
                else try {
                    AppLog.info("BridgeServer", "request=$request operation=${command.first()} accepted")
                    when (command.first()) {
                        "roa-hook" -> roa.command(command[1])
                        "roa-config" -> RearCameraConfiguration.enable()
                        else -> runner.run(command, parsed.timeout)
                    }
                } finally { execution.unlock() }
                output.writeInt(BridgePolicy.MAGIC)
                output.writeUTF(request)
                output.writeInt(result.exitCode)
                output.writeUTF(result.output.take(8000))
                output.writeBoolean(result.timedOut)
                output.flush()
            } catch (error: Exception) {
                AppLog.error("BridgeServer", "request-failed no-replay", error)
            }
        }
    }
}

