package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.compat.VehiclePlatform
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Root-owned preference; live hooks require the short authenticated VIP lease. No seat polling. */
object SeatMemoryActivation {
    private val flag = File("/data/local/voyahiran-seat-memory-enabled")
    private val sourceRuntime = File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
    private val directory = File("/data/local/tmp/voyahiran-bridge/seat-memory")
    private val runtime = File(directory, "frida-inject")
    private var started = false
    private var verifiedIdentity = ""
    private data class Attachment(val role: String, val packageName: String,
        @Volatile var child: Process? = null, var pid: String = "", var retryAt: Long = 0,
        var launchedAt: Long = 0, @Volatile var ready: Boolean = false,
        @Volatile var failed: Boolean = false,
        var stopping: Boolean = false, @Volatile var disabled: Boolean = false)
    private val attachments by lazy { listOf(Attachment("vehicle", "com.qinggan.app.vehicle"),
        if (VehiclePlatform.isH97a) Attachment("launcher", "com.qinggan.app.launcher")
        else Attachment("hiboard", "com.qinggan.app.hiboard")) }

    @Synchronized fun command(operation: String): CommandResult {
        if (!VehiclePlatform.localProfileTest) return CommandResult(126, "Unsupported vehicle variant")
        if (operation !in listOf("enable", "disable", "status")) return CommandResult(126, "Operation denied")
        if (operation == "enable" && !BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP required")
        return try {
            if (operation == "enable") {
                val temp = File(flag.path + ".new"); temp.writeText("1"); check(temp.renameTo(flag))
            }
            if (operation == "disable") check(!flag.exists() || flag.delete())
            AppLog.info("SeatMemoryActivation", "operation=$operation desired=${flag.exists()} vip=${BridgeSubscriptionSession.vipActive}")
            CommandResult(0, "seat_memory_desired=${flag.exists()}")
        } catch (e: Exception) { CommandResult(1, e.toString()) }
    }
    @Synchronized fun start() {
        if (started || !VehiclePlatform.localProfileTest) return
        started = true
        thread(name = "voyah-seat-memory-watch", isDaemon = true) {
            // A bridge restart must not leave an old injector competing for the same methods.
            runCatching { cleanOrphans(null) }
                .onFailure { AppLog.error("SeatMemoryActivation", "Orphan cleanup failed", it) }
            while (true) {
                val enabled = flag.exists() && BridgeSubscriptionSession.vipActive
                // One /proc scan for both OEM processes; no CAN reads or writes.
                val pids = if (enabled) File("/proc").listFiles().orEmpty()
                    .filter { it.name.all(Char::isDigit) }.mapNotNull { dir ->
                        runCatching { File(dir, "cmdline").readText().substringBefore('\u0000') to dir.name }.getOrNull()
                    }.toMap() else emptyMap()
                for (entry in attachments) try {
                    if (!enabled) stop(entry) else tick(entry, pids[entry.packageName].orEmpty())
                } catch (e: Exception) {
                    stop(entry); entry.retryAt = now() + 30000
                    AppLog.error("SeatMemoryActivation", "${entry.role} unavailable; retry in 30s", e)
                }
                Thread.sleep(2000)
            }
        }
    }
    private fun now() = android.os.SystemClock.elapsedRealtime()
    private fun tick(entry: Attachment, pid: String) {
        if (entry.stopping) { stop(entry); return }
        if (entry.child != null && (entry.child?.isAlive != true || entry.pid != pid || entry.failed)) {
            stop(entry); entry.retryAt = now() + 10000
        }
        if (pid.isEmpty()) return
        if (entry.child == null) {
            if (now() < entry.retryAt) return
            val identity = "${sourceRuntime.length()}:${sourceRuntime.lastModified()}"
            if (identity != verifiedIdentity) {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                sourceRuntime.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
                }
                check(digest.digest().joinToString("") { "%02x".format(it) } == File(sourceRuntime.path + ".sha256").readText().trim())
                check(directory.isDirectory || directory.mkdirs())
                check(directory.setReadable(false, false) && directory.setReadable(true, true))
                check(directory.setExecutable(false, false) && directory.setExecutable(true, true))
                check(directory.setWritable(false, false) && directory.setWritable(true, true))
                val temp = File(directory, "frida-inject.new")
                sourceRuntime.copyTo(temp, overwrite = true)
                check(temp.setExecutable(true, true) && temp.renameTo(runtime))
                verifiedIdentity = identity
            }
            val script = File(runtime.parentFile, "seat-memory-${entry.role}.js")
            script.writeText("const ROLE = '${entry.role}';\n" + SeatMemoryHookScript.source)
            check(script.setReadable(false, false) && script.setReadable(true, true))
            val context = File("/proc/self/attr/current").readText().trim().trimEnd('\u0000')
            val prefix = if (android.os.Build.VERSION.SDK_INT == 28 && context == "u:r:shell:s0") {
                val su = listOf("/system/xbin/su", "/system/bin/su").firstOrNull { File(it).canExecute() }
                    ?: error("OEM su unavailable")
                val result = ProcessCommandRunner(useBridge = false).run(listOf(su, "0", "/system/bin/id", "-Z"), 3)
                check(result.exitCode == 0 && !result.timedOut && result.output.trim() == "u:r:su:s0")
                listOf(su, "0")
            } else emptyList()
            cleanOrphans(script.path)
            entry.ready = false; entry.failed = false; entry.disabled = false; entry.stopping = false
            entry.pid = pid; entry.launchedAt = now()
            val child = ProcessBuilder(prefix + listOf(runtime.path, "-p", pid, "-s", script.path, "-i"))
                .redirectErrorStream(true).start()
            entry.child = child
            thread(name = "voyah-seat-${entry.role}", isDaemon = true) {
                try { child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    AppLog.info("SeatMemoryActivation", "${entry.role}: ${line.take(1200)}")
                    if (entry.child === child) {
                        if (line.startsWith("VI_SEAT_READY ")) entry.ready = true
                        if (line.startsWith("VI_SEAT_ERROR ")) entry.failed = true
                        if (line == "VI_SEAT_DISABLED") entry.disabled = true
                    }
                } } } catch (e: Exception) { AppLog.info("SeatMemoryActivation", "${entry.role} output closed") }
            }
        }
        check(entry.ready || now() - entry.launchedAt < 15000) { "Hook readiness not confirmed: ${entry.role}" }
        if (entry.ready && flag.exists() && BridgeSubscriptionSession.vipActive) {
            entry.child?.outputStream?.let { it.write("ping\n".toByteArray()); it.flush() }
        }
    }
    /** Kill only our private executable, optionally restricted to one fixed script. */
    private fun cleanOrphans(script: String?) {
        File("/proc").listFiles().orEmpty().filter { it.name.all(Char::isDigit) }.forEach { dir ->
            val arguments = runCatching { File(dir, "cmdline").readText().split('\u0000') }.getOrNull()
                ?: return@forEach
            if (arguments.firstOrNull() == runtime.path && (script == null || arguments.contains(script))) {
                android.os.Process.killProcess(dir.name.toInt())
            }
        }
    }
    private fun stop(entry: Attachment) {
        val child = entry.child ?: return
        val targetAlive = runCatching {
            File("/proc/${entry.pid}/cmdline").readText().substringBefore('\u0000') == entry.packageName
        }.getOrDefault(false)
        if (entry.ready && !entry.failed && child.isAlive && targetAlive) {
            if (!entry.stopping) { entry.disabled = false; entry.stopping = true }
            runCatching { child.outputStream.write("disable\n".toByteArray()); child.outputStream.flush() }
            // Keep dormant, guarded hooks until the main-thread panel reset is acknowledged.
            // This avoids leaving a Guest user's previously open controls usable after VIP loss.
            if (!entry.disabled) return
        }
        entry.child = null; entry.ready = false; entry.pid = ""; entry.stopping = false
        child.destroy()
        if (!child.waitFor(1, TimeUnit.SECONDS)) child.destroyForcibly()
        runCatching { cleanOrphans(File(directory, "seat-memory-${entry.role}.js").path) }
            .onFailure { AppLog.error("SeatMemoryActivation", "${entry.role} cleanup failed", it) }
        AppLog.info("SeatMemoryActivation", "${entry.role} detached; factory policy restored")
    }
}
