package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.navigation.NeshanConfig
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** OEM music presentation only. No raw CAN frames, display routing, or navigation ownership. */
internal object ClusterMusicBridge {
    private const val PACKAGE = "com.qinggan.instrumentcard"
    @Volatile private var frame: String? = null
    @Volatile private var received = 0L
    private var started = false
    @Volatile private var child: Process? = null
    private var pid = ""
    private var retryAt = 0L
    private var attachingAt = 0L
    @Volatile private var ready = false
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized fun command(action: String): CommandResult {
        if (action == "clear") { frame = null; return CommandResult(0, "Cluster music released") }
        if (!BridgeSubscriptionSession.active) return CommandResult(126, "Subscription inactive")
        val file = File("/data/user/0/com.abrar.voyahiran/files/cluster-music.json")
        require(file.length() in 2..200_000) { "Invalid music frame size" }
        val text = file.readText()
        val json = JSONObject(text)
        require(SystemClock.elapsedRealtime() - json.getLong("at") in 0..5000) { "Expired music frame" }
        require(json.getString("package").matches(Regex("[A-Za-z0-9_.]{1,150}")))
        frame = text; received = SystemClock.elapsedRealtime()
        if (!started) {
            started = true
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ClusterMusicBridge").apply { isDaemon = true } }
                .scheduleWithFixedDelay(::tick, 0, 200, TimeUnit.MILLISECONDS)
        }
        return CommandResult(0, "Cluster music queued")
    }
    private fun tick() {
        try {
            val value = frame?.takeIf { !ClusterMapBridge.presenting && BridgeSubscriptionSession.active && SystemClock.elapsedRealtime() - received < 5000 }
            if (value == null) { close(); return }
            if (child?.isAlive != true) {
                close()
                if (SystemClock.elapsedRealtime() < retryAt) return
                retryAt = SystemClock.elapsedRealtime() + 30000
                val modernInstalled = runner.run(listOf("/system/bin/pm", "path", PACKAGE), 3).output.lineSequence().any { it.startsWith("package:") }
                val legacy = !modernInstalled && android.os.Build.VERSION.SDK_INT == 28
                check(modernInstalled || legacy) { "No supported OEM cluster package" }
                val targetPackage = if (legacy) "com.qinggan.canbus.service" else PACKAGE
                val target = runner.run(listOf("/system/bin/pidof", targetPackage), 2).output.trim()
                check(target.matches(Regex("[1-9][0-9]*"))) { "OEM instrumentcard is not running/available" }
                check(File("/proc/$target/cmdline").readText().substringBefore('\u0000') == targetPackage)
                val executable = File("/data/local/tmp/voyahiran-bridge/neshan/frida-inject")
                val hash = File("/data/local/tmp/voyahiran-bridge/roa/frida-inject.sha256").readText().trim()
                check(NeshanConfig.fileHash(executable) == hash) { "Injector integrity check failed" }
                val script = File(executable.parentFile, "cluster-music.js")
                NeshanConfig.save(script, if (legacy) ClusterMusicLegacyScript.source else ClusterMusicScript.source)
                val process = ProcessBuilder(executable.path, "-p", target, "-s", script.path, "-i").redirectErrorStream(true).start()
                child = process; pid = target; attachingAt = SystemClock.elapsedRealtime()
                thread(name = "ClusterMusicOutput", isDaemon = true) {
                    runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach {
                        if (child === process) {
                            if (it == "VI_CLUSTER_MUSIC_READY") ready = true
                            AppLog.changed("ClusterMusic", it.take(400))
                        }
                    } } }
                }
                AppLog.info("ClusterMusic", "OEM adapter attaching package=$targetPackage pid=$pid legacy=$legacy sdk=${android.os.Build.VERSION.SDK_INT}")
            }
            check(ready || SystemClock.elapsedRealtime() - attachingAt < 10000) { "OEM adapter initialization timed out" }
            if (ready) retryAt = 0L
            if (ready && value != lastSent) {
                child!!.outputStream.apply { write((value + "\n").toByteArray()); flush() }
                lastSent = value
            }
        } catch (e: Exception) {
            AppLog.changed("ClusterMusic", "adapter unavailable: ${e.message}")
            close(); retryAt = SystemClock.elapsedRealtime() + 30000
        }
    }
    private var lastSent: String? = null
    private fun close() {
        val old = child; child = null; ready = false; lastSent = null
        if (old != null) {
            runCatching { old.outputStream.apply { write("clear\n".toByteArray()); flush() } }
            // Give the main-thread restoration a chance to finish before unloading the hooks.
            Thread.sleep(150)
            old.destroy()
            if (!old.waitFor(1, TimeUnit.SECONDS)) old.destroyForcibly()
        }
    }
}
