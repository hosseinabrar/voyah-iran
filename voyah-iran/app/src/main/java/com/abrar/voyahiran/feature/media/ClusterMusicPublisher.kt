package com.abrar.voyahiran.feature.media

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.launcher.LauncherMediaObserver
import com.abrar.voyahiran.feature.launcher.LauncherMediaState
import com.abrar.voyahiran.feature.launcher.LauncherArtwork
import com.abrar.voyahiran.feature.launcher.LauncherClusterMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Shares the launcher's event-driven source selection; the heartbeat only renews ownership. */
internal class ClusterMusicPublisher(private val context: Context, private val scope: CoroutineScope) {
    private var state = LauncherMediaState()
    private var observedAt = 0L
    private var job: Job? = null
    private var observer: LauncherMediaObserver? = null
    private var sent = false
    private var coverKey: Any? = null
    private var cover = ""
    private val runner = ProcessCommandRunner()
    private val artwork = LauncherArtwork(context)
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val preferences = context.getSharedPreferences("media_controls", Context.MODE_PRIVATE)
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updates.trySend(Unit) }
    private val frameFile = File(context.filesDir, "cluster-music.json")
    fun start() {
        stop()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        observer = LauncherMediaObserver(context, scope) { state = it; observedAt = SystemClock.elapsedRealtime(); updates.trySend(Unit) }.also { it.start() }
        job = scope.launch {
            while (isActive) {
                val value = state
                val allowed = ClusterMusicPreferences.enabled(context) &&
                    (context.applicationContext as VoyahIranApplication).subscriptions.allowed
                val mapEnabled = LauncherClusterMap.available(context)
                // H97A has a different OEM renderer. Keep its previous third-party
                // music policy; the single-image compositor targets H97X/H97C.
                val legacySource = android.os.Build.VERSION.SDK_INT != 28 || withContext(Dispatchers.IO) {
                    runCatching {
                        val oem = runCatching { JSONObject(android.provider.Settings.Global.getString(context.contentResolver, "voyah_iran_launcher_media")) }.getOrNull()
                        val factoryPlaying = oem?.optBoolean("playing") == true &&
                            SystemClock.elapsedRealtime() - oem.optLong("at", -10000) in 0..6500
                        val info = context.packageManager.getApplicationInfo(value.sourcePackage, 0)
                        !factoryPlaying && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                            value.sourcePackage != context.packageName && value.controller != null && value.oemSource.isEmpty()
                    }.getOrDefault(false)
                }
                try {
                    if ((allowed || mapEnabled) && legacySource) {
                        val elapsed = if (value.playing) SystemClock.elapsedRealtime() - observedAt else 0
                        withContext(Dispatchers.IO) { publish(value, elapsed, allowed, mapEnabled) }
                        sent = true
                    } else if (sent) {
                        withContext(Dispatchers.IO) { runner.run(listOf("su", "0", "cluster-music", "clear"), 3) }
                        sent = false; coverKey = null; cover = ""
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLog.changed("ClusterMusic", "publish failed: ${e.message}")
                }
                withTimeoutOrNull(1000) { updates.receive() }
            }
        }
    }
    private fun publish(value: LauncherMediaState, elapsed: Long, musicEnabled: Boolean, mapEnabled: Boolean) {
        val key = listOf(value.sourcePackage, value.title, value.cover, value.coverUri)
        if (key != coverKey) {
            coverKey = key
            cover = runCatching {
                val bitmap = value.cover ?: artwork.load(value.coverUri)
                bitmap?.let {
                    val ratio = 256f / maxOf(it.width, it.height)
                    val scaled = Bitmap.createScaledBitmap(it, (it.width * ratio).toInt().coerceAtLeast(1), (it.height * ratio).toInt().coerceAtLeast(1), true)
                    val bytes = ByteArrayOutputStream().also { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out) }.toByteArray()
                    if (scaled !== it) scaled.recycle()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }.orEmpty()
            }.getOrDefault("")
        }
        val frame = JSONObject().put("package", value.sourcePackage.ifBlank { context.packageName }).put("source", value.source.take(100))
            .put("musicEnabled", musicEnabled).put("mapEnabled", mapEnabled)
            // Either checkbox gives the compositor ownership of the whole region.
            // The compositor uses musicEnabled to restore OEM content in map-only fallback.
            .put("idle", !value.playing)
            .put("title", value.title.take(300)).put("artist", value.artist.take(300))
            .put("album", value.controller?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM).orEmpty().take(300))
            .put("playing", value.playing).put("duration", value.duration.coerceAtLeast(0))
            .put("playbackAt", value.controller?.playbackState?.lastPositionUpdateTime ?: 0)
            .put("position", ClusterMusicPolicy.position(value.position, elapsed, value.duration))
            .put("at", SystemClock.elapsedRealtime()).put("cover", cover)
        val temp = File(frameFile.path + ".new")
        temp.writeText(frame.toString()); check(temp.renameTo(frameFile))
        val result = runner.run(listOf("su", "0", "cluster-music", "refresh"), 3)
        check(result.exitCode == 0 && !result.timedOut) { result.output }
    }
    fun stop() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observer?.stop(); observer = null; job?.cancel(); job = null
        // The bridge and OEM adapter independently expire the last frame after five seconds.
        state = LauncherMediaState(); sent = false
    }
}
