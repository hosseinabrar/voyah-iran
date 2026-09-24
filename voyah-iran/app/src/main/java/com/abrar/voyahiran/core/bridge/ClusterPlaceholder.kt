package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.navigation.NeshanConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Lazy, narrowly scoped formatter for our sentinel. No connection to a second cluster socket. */
internal object ClusterPlaceholder {
    const val MISSING_DISTANCE = -2147483000
    @Volatile private var process: Process? = null
    @Volatile private var ready = false
    private var retryAt = 0L
    private var pid = ""
    private var checkAt = 0L
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized fun available(): Boolean {
        try {
            if (process?.isAlive == true && ready && SystemClock.elapsedRealtime() < checkAt) return true
            checkAt = SystemClock.elapsedRealtime() + 1000
            val target = runner.run(listOf("/system/bin/pidof", "com.qinggan.cluster"), 2).output.trim()
            if (!target.matches(Regex("[1-9][0-9]*"))) { close(); return false }
            if (process != null && (process?.isAlive != true || pid != target)) close()
            if (process == null) {
                if (SystemClock.elapsedRealtime() < retryAt) return false
                retryAt = SystemClock.elapsedRealtime() + 30000
                check(File("/proc/$target/cmdline").readText().substringBefore('\u0000') == "com.qinggan.cluster")
                val executable = File("/data/local/tmp/voyahiran-bridge/neshan/frida-inject")
                val expected = File("/data/local/tmp/voyahiran-bridge/roa/frida-inject.sha256").readText().trim()
                check(NeshanConfig.fileHash(executable) == expected) { "Injector integrity check failed" }
                val script = File(executable.parentFile, "cluster-placeholder.js")
                NeshanConfig.save(script, ClusterPlaceholderScript.source)
                val child = ProcessBuilder(executable.path, "-p", target, "-s", script.path, "-i").redirectErrorStream(true).start()
                process = child; pid = target; ready = false
                thread(name = "cluster-placeholder-output", isDaemon = true) {
                    try { child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        if (process === child) {
                            if (line == "VI_CLUSTER_PLACEHOLDER_READY") ready = true
                            AppLog.changed("ClusterPlaceholder", line.take(300))
                        }
                    } } } catch (_: Exception) { /* Closed by owner. */ }
                }
                // Only the cluster worker waits; startup and the UI remain nonblocking.
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (!ready && child.isAlive && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
                if (!ready) { close(); return false }
            }
            process!!.outputStream.apply { write("ping\n".toByteArray()); flush() }
            return ready
        } catch (e: Exception) {
            AppLog.changed("ClusterPlaceholder", "unavailable: ${e.message}")
            close(); retryAt = SystemClock.elapsedRealtime() + 30000
            return false
        }
    }
    @Synchronized fun close() {
        val old = process; process = null; ready = false; pid = ""
        if (old != null) {
            runCatching { old.outputStream.apply { write("disable\n".toByteArray()); flush() } }
            old.destroy()
            if (!old.waitFor(1, TimeUnit.SECONDS)) old.destroyForcibly()
        }
    }
}
