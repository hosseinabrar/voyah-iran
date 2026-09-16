package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Root bridge owns one local injector; no network listener, no modification of OEM/frida files. */
class RoaHookManager {
    private val directory = File("/data/local/tmp/voyahiran-bridge/roa")
    private val flag get() = File(directory, "enabled")
    private val executable get() = File(directory, "frida-inject")
    private val script get() = File(directory, "hook_camera_front_roa_switch.js")
    private val runner = ProcessCommandRunner(useBridge = false)
    @Volatile private var child: Process? = null
    private var target = ""
    @Volatile private var ready = false
    @Volatile private var disabled = false
    @Volatile private var failure: String? = null
    private var retryAfter = 0L
    private var verified = false

    fun start() {
        // init may have terminated the parent. Only clean up our own exact executable.
        File("/proc").listFiles()?.filter { it.name.all(Char::isDigit) }?.forEach {
            if (runCatching { File(it, "cmdline").readText().substringBefore('\u0000') == executable.path }.getOrDefault(false)) {
                runner.run(listOf("/system/bin/kill", "-TERM", it.name), 3)
            }
        }
        thread(name = "voyah-roa-watch", isDaemon = true) {
            while (true) {
                try { synchronized(this) { tick() } }
                catch (e: Exception) { failure = e.message; AppLog.error("ROAHook", "watch failed", e); retryAfter = now() + 30000 }
                Thread.sleep(2000)
            }
        }
    }
    private fun now() = android.os.SystemClock.elapsedRealtime()
    private fun verifyFiles() {
        if (verified) return
        check(executable.isFile && script.isFile) { "ROA runtime missing; update installer" }
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        for (name in listOf("frida-inject", "hook_camera_front_roa_switch.js")) {
            val expected = File(directory, "$name.sha256").readText().trim()
            val actual = File(directory, name).inputStream().use { stream ->
                sha.reset(); val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break; sha.update(buffer, 0, n) }
                sha.digest().joinToString("") { "%02x".format(it) }
            }
            check(expected == actual) { "ROA runtime hash mismatch: $name" }
        }
        verified = true
    }
    @Synchronized fun command(action: String): CommandResult = try {
        when (action) {
            "enable" -> {
                verifyFiles()
                failure = null; retryAfter = 0
                File(directory, "enabled.new").writeText("1\n")
                check(File(directory, "enabled.new").renameTo(flag))
                try { tick(); check(failure == null) { failure.orEmpty() } }
                catch (e: Exception) { flag.delete(); stop(); throw e }
            }
            "disable" -> { check(!flag.exists() || flag.delete()); stop(); failure = null }
            "status" -> Unit
            else -> error("Unsupported ROA operation")
        }
        val state = when {
            failure != null -> "ERROR"
            !flag.exists() -> "DISABLED"
            child?.isAlive == true && ready -> "ATTACHED"
            else -> "WAITING"
        }
        CommandResult(0, "ROA_HOOK=$state")
    } catch (e: Exception) {
        failure = e.message
        AppLog.error("ROAHook", "action=$action failed", e)
        CommandResult(1, "ROA hook failed: ${e.message}")
    }
    private fun pid(): String {
        val result = runner.run(listOf("/system/bin/pidof", "com.qinggan.camera"), 3)
        val candidates = result.output.trim().split(Regex("\\s+")).filter { it.matches(Regex("[1-9][0-9]*")) }
        return candidates.singleOrNull { p -> runCatching {
            File("/proc/$p/cmdline").readText().substringBefore('\u0000') == "com.qinggan.camera"
        }.getOrDefault(false) }.orEmpty()
    }
    private fun tick() {
        if(!BridgeSubscriptionSession.active) { stop();return }
        if (!flag.exists()) { if (child != null) stop(); return }
        verifyFiles()
        val current = pid()
        if (child != null && !child!!.isAlive) {
            stop(); failure = "Injector exited; waiting before retry"; retryAfter = now() + 30000
        } else if (child != null && current != target) stop()
        if (current.isEmpty()) return
        if (child == null && now() >= retryAfter) {
            ready = false; disabled = false; failure = null; target = current
            val process = ProcessBuilder(executable.path, "-p", current, "-s", script.path, "-i").redirectErrorStream(true).start()
            child = process
            thread(name = "voyah-roa-output", isDaemon = true) {
                try {
                    process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        AppLog.info("ROAHook", "pid=$current ${line.take(1500)}")
                        if (child === process) {
                            if (line == "VI_ROA_READY") ready = true
                            if (line == "VI_ROA_DISABLED") { disabled = true; ready = false }
                            if (line.contains("VI_ROA_ERROR")) failure = line.take(500)
                        }
                    } }
                } catch (e: Exception) { AppLog.info("ROAHook", "output closed: ${e.message}") }
            }
            val deadline = now() + 12000
            while (process.isAlive && !ready && failure == null && now() < deadline) Thread.sleep(100)
            if (!ready || failure != null) {
                val reason = failure ?: "Injector did not confirm hook readiness"
                stop(); failure = reason; retryAfter = now() + 30000
                error(reason)
            }
            AppLog.info("ROAHook", "attached camera pid=$current")
        }
        child?.let { it.outputStream.write("ping\n".toByteArray()); it.outputStream.flush() }
    }
    private fun stop() {
        val process = child ?: return
        var cleanupConfirmed = !process.isAlive
        try {
            if (process.isAlive) {
                process.outputStream.write("disable\n".toByteArray()); process.outputStream.flush()
                val deadline = now() + 3000
                while (!disabled && process.isAlive && now() < deadline) Thread.sleep(50)
                cleanupConfirmed = disabled || !process.isAlive
            }
        } finally {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            child = null; target = ""; ready = false; disabled = false
            AppLog.info("ROAHook", "injector stopped cleanupConfirmed=$cleanupConfirmed")
        }
        check(cleanupConfirmed) { "ROA view cleanup not acknowledged" }
    }
}


