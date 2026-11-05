package com.abrar.voyahiran.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.system.Os
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Root bridge worker: package events + one boot reconciliation, never polling or a CAN worker. */
internal object MicrogAutoSetup {
    internal const val CORE = "com.google.android.gms"
    internal const val COMPANION = "com.android.vending"
    internal const val CERT = "9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165"
    internal val assets = mapOf(
        "companion.apk" to "9b8616011cc8f026bcbbe094df850794abb9d6d9f23214d7a8f8c4d1033ea012",
        "provision.dex" to "e1b763008669bca6bd59487c333ab4b0fd9901cec97d972e9cab93608e0e760d"
    )
    private val worker by lazy { HandlerThread("MicrogSetup").apply { start() } }
    private val handler by lazy { Handler(worker.looper) }
    private val runner = ProcessCommandRunner(useBridge = false)
    private var context: Context? = null
    private var observing = false
    private var started = false
    private var attempt = 0
    private val delays = longArrayOf(2000, 5000, 15000, 30000, 60000)
    private val reconcile: Runnable = Runnable {
        try { prepare(); attempt = 0 }
        catch (e: Exception) {
            AppLog.error("MicrogSetup", "Preparation failed attempt=${attempt + 1}", e)
            if (attempt < delays.size) handler.postDelayed(reconcileTask(), delays[attempt++])
        }
    }
    // Avoid referring to an incompletely initialized property inside its initializer.
    private fun reconcileTask(): Runnable = reconcile

    @Synchronized fun start() {
        if (started) return
        started = true
        handler.post(reconcile)
    }

    internal fun relevant(action: String?, pkg: String?) = pkg == CORE &&
        action in setOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED)

    private fun systemContext(): Context {
        context?.let { return it }
        val type = Class.forName("android.app.ActivityThread")
        val thread = type.getMethod("currentActivityThread").invoke(null) ?: type.getMethod("systemMain").invoke(null)
        return (type.getMethod("getSystemContext").invoke(thread) as Context).also { context = it }
    }

    private fun installed(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun prepare() {
        val c = systemContext()
        if (!observing) {
            c.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!relevant(intent.action, intent.data?.schemeSpecificPart)) return
                    attempt = 0
                    handler.removeCallbacks(reconcile)
                    handler.postDelayed(reconcile, 1000)
                }
            }, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
            }, null, handler)
            observing = true
        }
        val pm = c.packageManager
        if (!installed(pm, CORE)) return
        val cert = CERT.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // Checks actual stored identity; the framework's presentation hook does not affect this API.
        if (!pm.hasSigningCertificate(CORE, cert, PackageManager.CERT_INPUT_SHA256)) {
            AppLog.info("MicrogSetup", "Non-microG Google services left unchanged")
            return
        }
        val dir = File("/data/local/voyahiran-microg")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create microG staging directory" }
        check(dir.canonicalPath == dir.absolutePath) { "Invalid microG staging directory" }
        check(Os.stat(dir.path).st_uid == 0) { "Invalid microG staging owner" }
        Os.chmod(dir.path, 448) // root-only 0700
        extract(dir, "provision.dex")
        val invoke = listOf("/system/bin/env", "CLASSPATH=${dir.path}/provision.dex", "/system/bin/app_process", "/system/bin", "MicrogProvision")
        check(run(invoke).lineSequence().any { it.trim() == "MICROG_OFFICIAL_READY" }) { "microG identity changed" }
        if (!installed(pm, COMPANION)) {
            val apk = extract(dir, "companion.apk")
            // Streaming also works on H97A, where system_server cannot open shell-owned APK paths.
            // All words here are fixed root-owned paths or a measured numeric size; no external input.
            val output = run(listOf("/system/bin/sh", "-c", "cat ${apk.path} | /system/bin/pm install --user 0 -S ${apk.length()}"))
            check(output.lineSequence().any { it.trim() == "Success" } && installed(pm, COMPANION)) { "Companion installation not confirmed" }
        }
        val permissions = mutableListOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION")
        if (Build.VERSION.SDK_INT >= 29) permissions += "ACCESS_BACKGROUND_LOCATION"
        permissions.forEach { permission ->
            val full = "android.permission.$permission"
            if (pm.checkPermission(full, CORE) != PackageManager.PERMISSION_GRANTED)
                run(listOf("/system/bin/pm", "grant", "--user", "0", CORE, full))
            check(pm.checkPermission(full, CORE) == PackageManager.PERMISSION_GRANTED) { "microG permission not granted: $permission" }
        }
        check(run(invoke + "verify").lineSequence().any { it.trim() == "MICROG_OFFICIAL_READY" }) { "microG framework support not ready" }
        File(dir, "companion.apk").delete()
        AppLog.info("MicrogSetup", "Official microG ready; Companion, location and framework compatibility verified")
    }

    private fun run(args: List<String>): String {
        val result = runner.run(args, 30)
        check(result.exitCode == 0 && !result.timedOut) { "microG setup operation failed exit=${result.exitCode}" }
        return result.output
    }

    private fun extract(dir: File, name: String): File {
        val expected = assets.getValue(name)
        val target = File(dir, name)
        fun valid(file: File) = file.isFile && file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expected
        }
        if (valid(target)) return target
        val temp = File(dir, "$name.tmp")
        try {
            ZipFile("/data/local/tmp/voyahiran-bridge/bridge.apk").use { zip ->
                val entry = checkNotNull(zip.getEntry("assets/microg/$name")) { "Missing bundled microG asset" }
                zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
            }
            check(valid(temp)) { "Bundled microG asset checksum mismatch" }
            Os.chmod(temp.path, 384) // 0600
            check(temp.renameTo(target)) { "Cannot stage microG asset" }
            return target
        } finally { temp.delete() }
    }
}
