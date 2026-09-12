package com.abrar.voyahiran.core.shell

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean = false)

fun interface CommandRunner {
    fun run(arguments: List<String>, timeoutSeconds: Long): CommandResult
}

/** No shell interpolation at this boundary. Output is drained concurrently and bounded. */
class ProcessCommandRunner(private val useBridge: Boolean = true) : CommandRunner {
    override fun run(arguments: List<String>, timeoutSeconds: Long): CommandResult {
        if (useBridge && arguments.take(2) == listOf("su", "0")) {
            return com.abrar.voyahiran.core.bridge.BridgeClient.run(arguments.drop(2), timeoutSeconds)
        }
        val log = com.abrar.voyahiran.core.logging.AppLog
        val requestId = log.nextId()
        // OEM images ship su outside the application PATH. Resolve it before launch;
        // never retry a command after launch because some vehicle commands toggle state.
        val executableArguments = if (arguments.firstOrNull() == "su") {
            val path = listOf("/system/xbin/su", "/system/bin/su")
                .firstOrNull { java.io.File(it).isFile }
            if (path != null) listOf(path) + arguments.drop(1) else arguments
        } else arguments
        val operation = when {
            arguments.firstOrNull() == "/system/bin/service" && arguments.getOrNull(2) == "alarm" -> "AlarmManager-setTimeZone"
            arguments.contains("-c") -> "${arguments.firstOrNull()}:script"
            arguments.contains("service") -> "CAN-service-call"
            arguments.contains("ime") -> "IME-${arguments.getOrNull(arguments.indexOf("ime") + 1)}"
            arguments.contains("am") -> "ActivityManager-${arguments.getOrNull(arguments.indexOf("am") + 1)}"
            else -> arguments.firstOrNull().orEmpty()
        }
        log.info("Shell", "request=$requestId operation=$operation timeoutSeconds=$timeoutSeconds")
        val process = try {
            ProcessBuilder(executableArguments).redirectErrorStream(true).start()
        } catch (error: IOException) {
            // Startup errors include the executable and errno, not command output.
            val detail = error.message.orEmpty().replace('\n', ' ').take(500)
            val cause = error.cause?.message.orEmpty().replace('\n', ' ').take(300)
            log.error("Shell", "request=$requestId process-start-failed executable=${executableArguments.firstOrNull()} detail=$detail cause=$cause", error)
            return CommandResult(127, error.message.orEmpty())
        }
        process.outputStream.close()
        val output = StringBuilder()
        val reader = thread(name = "voyah-command-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            val remaining = 16384 - output.length
                            if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                        }
                    }
                }
            } catch (_: IOException) { /* Stream is closed when a timed-out process is killed. */ }
        }
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(1000)
        val diagnostic = synchronized(output) { output.toString() }
        val indicators = listOf("Permission denied", "SecurityException", "not found", "Unknown command", "Error", "Exception")
            .filter { diagnostic.contains(it, ignoreCase = true) }
        if (indicators.isNotEmpty()) log.info("Shell", "request=$requestId indicators=$indicators")
        log.info("Shell", "request=$requestId operation=$operation exit=${if (completed) process.exitValue() else -1} timedOut=${!completed}")
        return CommandResult(
            if (completed) process.exitValue() else -1,
            synchronized(output) { output.toString().trim() },
            timedOut = !completed,
        )
    }
}
