package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One bounded launcher attachment, restarted if launcher or injector exits. */
object OemNavigationManager {
    private val runtime = File("/data/local/tmp/voyahiran-bridge/roa/frida-inject")
    private val script = File("/data/local/tmp/voyahiran-bridge/navigation-policy.js")
    private var child: Process? = null
    private var target = ""
    @Volatile private var ready = false
    @Volatile private var error: String? = null
    private var retryAt = 0L
    private var started = false
    private var fallback: Boolean? = null
    private var drawerAccessCheckAt = 0L

    private fun ensureDrawerAccess() {
        if (fallback != true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < drawerAccessCheckAt) return
        drawerAccessCheckAt = now + 30000
        val result = com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge = false)
            .run(LauncherDrawerAccess.arguments, 5)
        val configured = result.exitCode == 0 && !result.timedOut && result.output.contains("LAUNCHER_DRAWER_CONFIGURED")
        AppLog.changed("LauncherDrawerAccess", "configured=$configured; retry check in 30s")
    }

    private fun publishFallback(enabled: Boolean) {
        if (fallback == enabled) return
        val result = com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge = false).run(
            listOf("/system/bin/settings", "put", "global",
                com.abrar.voyahiran.feature.launcher.LauncherFallbackPolicy.SETTING, if (enabled) "1" else "0"), 3)
        if (result.exitCode == 0 && !result.timedOut) {
            fallback = enabled
            AppLog.info("Navigation", "launcher drawer fallback=$enabled")
        }
    }

    @Synchronized fun start() {
        if (started) return
        started = true
        publishDrawerReady(false)
        thread(name = "voyah-navigation-watch", isDaemon = true) {
            while (true) {
                try { publishFallback(com.abrar.voyahiran.core.compat.VehiclePlatform.launcherDrawer); ensureDrawerAccess(); tick() }
                catch (e: Exception) {
                    stop()
                    retryAt = android.os.SystemClock.elapsedRealtime() + 500
                    AppLog.error("Navigation", "Launcher policy unavailable; retry in 500ms", e)
                }
                Thread.sleep(if(child?.isAlive == true && ready) 2000 else 250)
            }
        }
    }
    private fun tick() {
        val drawerOnly = android.os.Build.VERSION.SDK_INT == 28
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkHooks && !drawerOnly) return
        // Android 9 only receives the isolated drawer/Home hooks, never Android 11 window hooks.
        if (drawerOnly && (!BridgeSubscriptionSession.vipActive || !LauncherAppsBridge.enabledForLegacy)) { if(child != null) stop(); return }
        if (android.os.SystemClock.elapsedRealtime() < retryAt) return
        val pid = File("/proc").listFiles().orEmpty().filter { it.name.all(Char::isDigit) }.singleOrNull {
            runCatching { File(it, "cmdline").readText().substringBefore('\u0000') == "com.qinggan.app.launcher" }.getOrDefault(false)
        }?.name.orEmpty()
        if (child != null && (child?.isAlive != true || target != pid)) {
            stop(); retryAt = android.os.SystemClock.elapsedRealtime() + 250; return
        }
        if (child != null) return
        if (pid.isEmpty()) return
        check(runtime.isFile) { "Navigation runtime missing; run installer" }
        val expected = File(runtime.path + ".sha256").readText().trim()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        runtime.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Navigation runtime hash mismatch" }
        // Drawer selection is model-based. No app inventory or OEM list reconciliation.
        val stockPassengerList = !com.abrar.voyahiran.core.compat.VehiclePlatform.launcherDrawer
        val fullScript = "globalThis.VI_STOCK_PASSENGER_LIST = $stockPassengerList;\n" +
            VehicleBarsScript.source + "\n" + OemNavigationScript.source + "\n" + PassengerDrawerScript.source + "\n" + LauncherAppsScript.source + "\n" + LauncherHomeScript.source + "\n" + LauncherTransferScript.source
        script.writeText(if(drawerOnly) LauncherAppsScript.source + "\n" + LauncherHomeScript.source else fullScript)
        check(script.setReadable(false, false) && script.setReadable(true, true))
        ready = false; error = null; target = pid
        val process = ProcessBuilder(runtime.path, "-p", pid, "-s", script.path, "-i").redirectErrorStream(true).start()
        child = process
        thread(name = "voyah-navigation-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    AppLog.info("Navigation", "pid=$pid " + line.take(1500))
                    if (child === process) {
                        if (line == "VI_DRAWER_READY" || (!drawerOnly && line == "VI_NAV_READY")) ready = true
                        if (line == "VI_DRAWER_READY") publishDrawerReady(true)
                        // An unsupported optional hook must not tear down another
                        // successfully attached launcher capability.
                    }
                } }
            } catch (e: Exception) { AppLog.info("Navigation", "output closed: " + e.message) }
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 12000
        while (process.isAlive && !ready && error == null && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        check(ready && error == null) { error ?: "Launcher hook readiness not confirmed" }
        AppLog.info("Navigation", "Launcher hooks attached pid=$pid; drawer selection depends only on vehicle variant")
    }
    private fun publishDrawerReady(value: Boolean) {
        com.abrar.voyahiran.core.shell.ProcessCommandRunner(useBridge=false).run(listOf(
            "/system/bin/settings","put","global","voyah_iran_drawer_hook_ready",if(value)"1" else "0"),2)
    }
    private fun stop() {
        publishDrawerReady(false)
        val process = child
        child = null; target = ""; ready = false
        process?.destroy()
        if (process?.waitFor(2, TimeUnit.SECONDS) == false) process.destroyForcibly()
    }
}
