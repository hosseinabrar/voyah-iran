package com.abrar.voyahiran.feature.navigation

import android.content.Context
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** No network, APK hashing or signature verification on the UI thread. */
internal object NeshanConfigStore {
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "neshan-config").apply { isDaemon = true } }
    @Volatile var profile: NeshanConfig.Profile? = null
        private set
    private var running = false
    private var lastStart = -60000L
    fun installedPackageChanged(context: Context) {
        profile = null
        val app = context.applicationContext
        worker.execute {
            try {
                apkIdentity = ""
                select(app, runCatching { NeshanConfig.verify(NeshanConfig.read(File(app.filesDir, NeshanConfig.FILE_NAME))) }.getOrNull())
            } catch (e: Exception) { AppLog.info("NeshanConfig", "package changed during selection: ${e.message}") }
            refresh(app)
        }
    }
    @Synchronized fun refresh(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (running || now - lastStart < 15000) return
        running = true; lastStart = now
        val app = context.applicationContext
        worker.execute { attempt(app, 0) }
    }
    private fun attempt(context: Context, number: Int) {
        val cache = File(context.filesDir, NeshanConfig.FILE_NAME)
        var success = false
        try {
            // Offline cache becomes usable immediately, before the HTTP request finishes.
            select(context, runCatching { NeshanConfig.verify(NeshanConfig.read(cache)) }.getOrNull())
            val result = NeshanConfigDownload(cache).update()
            select(context, result.catalog)
            AppLog.info("NeshanConfig", "revision=${result.catalog.revision} profiles=${result.catalog.profiles.size} changed=${result.changed}")
            success = true
        } catch (e: Exception) {
            AppLog.info("NeshanConfig", "refresh failed; keeping verified cache: ${e.message}")
        } finally {
            synchronized(this) { running = false }
        }
        // A vehicle can start before Wi-Fi. Retries are bounded and never block startup.
        if (!success && number < 3) worker.schedule({
            synchronized(this) { if (running) return@schedule; running = true }
            attempt(context, number + 1)
        }, longArrayOf(30, 120, 300)[number], TimeUnit.SECONDS)
    }
    private var apkIdentity = ""
    private var apkHash = ""
    @Suppress("DEPRECATION")
    private fun select(context: Context, catalog: NeshanConfig.Catalog?) {
        profile = null
        if (catalog == null) return
        try {
            val info = context.packageManager.getPackageInfo(NeshanConfig.PACKAGE, 0)
            val file = File(context.packageManager.getApplicationInfo(NeshanConfig.PACKAGE, 0).sourceDir)
            val identity = "${file.path}:${file.length()}:${file.lastModified()}:${info.longVersionCode}"
            if (identity != apkIdentity) { apkHash = NeshanConfig.fileHash(file); apkIdentity = identity }
            profile = catalog.profiles.singleOrNull { it.versionCode == info.longVersionCode &&
                it.versionName == info.versionName && it.apkSha256 == apkHash }
            AppLog.info("NeshanConfig", "installed=${info.longVersionCode} profile=${profile?.id ?: "unsupported"}")
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            AppLog.info("NeshanConfig", "Neshan not installed")
        }
    }
}
