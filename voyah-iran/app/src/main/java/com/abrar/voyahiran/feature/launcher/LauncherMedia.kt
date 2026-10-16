package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.feature.media.MediaControl
import org.json.JSONObject

data class LauncherMediaState(val source: String = "Music", val title: String = "no music", val artist: String = "",
    val cover: Bitmap? = null, val playing: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val controller: MediaController? = null, val radio: Boolean = false, val coverUri: String = "",
    val sourcePackage: String = "", val oemSource: String = "", val sourceIcon: android.graphics.drawable.Drawable? = null,
    val coverPending:Boolean = false) {
    val canSeek: Boolean get() = !radio && duration > 0 &&
        ((controller?.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L)
}

object LauncherMedia {
    internal fun firstArtworkUri(vararg values:String?):String=values.firstOrNull{!it.isNullOrBlank()}.orEmpty()
    private val memory = LauncherMediaMemory()
    private var lastOemSource = ""
    private var iconPackage = ""
    private var icon: android.graphics.drawable.Drawable? = null
    private fun appIcon(context: Context, pkg: String): android.graphics.drawable.Drawable? {
        if (pkg != iconPackage) {
            iconPackage = pkg
            icon = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
        }
        return icon
    }
    @Synchronized fun read(context: Context, sessions: List<MediaController> = runCatching {
        context.getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaControl.component(context))
    }.getOrDefault(emptyList())): LauncherMediaState {
        val oem = runCatching { JSONObject(Settings.Global.getString(context.contentResolver, "voyah_iran_launcher_media")) }.getOrNull()
        val oemFresh = oem != null && SystemClock.elapsedRealtime() - oem.optLong("at", -10000) in 0..6500
        val currentOemSource = if(oemFresh) oem!!.optString("source") else ""
        val sourceChanged = currentOemSource.isNotBlank() && currentOemSource != "NO" && lastOemSource.isNotBlank() && lastOemSource != currentOemSource
        if(currentOemSource.isNotBlank()) lastOemSource=currentOemSource
        val oemActive = oemFresh && oem!!.optBoolean("playing")
        val active = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING &&
            !(oemActive && it.packageName in setOf("com.qinggan.media", "com.qinggan.app.music", "com.qinggan.app.radio")) }
        if (active != null) {
            val m = active.metadata
            val state = active.playbackState
            val duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
            val elapsed = if (state != null && state.lastPositionUpdateTime > 0) ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong() else 0
            val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "no music" }
            return memory.remember(LauncherMediaState(runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(active.packageName, 0)).toString() }.getOrDefault("Music"),
                title, m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown" },
                m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                true, ((state?.position ?: 0) + elapsed).coerceAtLeast(0).let { if(duration > 0) it.coerceAtMost(duration) else it }, duration, active,
                sourcePackage = active.packageName, sourceIcon = appIcon(context, active.packageName),
                oemSource = if(active.packageName in setOf("com.qinggan.media", "com.qinggan.app.music", "com.qinggan.app.radio") && currentOemSource!="NO") currentOemSource else "",
                coverUri = firstArtworkUri(m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),m?.getString(MediaMetadata.METADATA_KEY_ART_URI),m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))))
        }
        if (oem != null && oemActive) {
            val radio = oem.optString("source") == "LOCAL_RADIO"
            val frequency = oem.optInt("frequency")
            val title = if (radio && frequency > 0) {
                if (oem.optString("band").contains("FM", true)) String.format(java.util.Locale.US, "%.1f MHz", frequency / 1000.0) else "$frequency kHz"
            } else oem.optString("title").ifBlank { if(radio) "No station" else if(oem.optString("source")=="BT_MUSIC") "Please play music" else "no music" }
            val duration=oem.optLong("duration").coerceAtLeast(0)
            val position=(oem.optLong("position") + if(oem.optLong("positionAt")>0) ((SystemClock.elapsedRealtime()-oem.optLong("positionAt"))*oem.optDouble("speed",0.0)).toLong() else 0).coerceAtLeast(0)
            return memory.remember(LauncherMediaState(when(oem.optString("source")) { "LOCAL_RADIO" -> "Radio"; "BT_MUSIC", "SYSTEM_BT_MUSIC" -> "Bluetooth"; "USB_MUSIC" -> "USB"; else -> oem.optString("source").replace('_', ' ') }, title,
                if(radio) oem.optString("band") else oem.optString("artist").ifBlank { "Unknown" }, playing = true, radio = radio, oemSource = oem.optString("source"), controller = sessions.firstOrNull { it.packageName in setOf("com.qinggan.media", "com.qinggan.app.music", "com.qinggan.app.radio") && it.playbackState?.state == PlaybackState.STATE_PLAYING }, position=if(duration>0)position.coerceAtMost(duration)else 0,duration=duration,coverUri = if(radio) "" else oem.optString("cover")))
        }
        val previous = memory.last
        val controller = sessions.firstOrNull { it.packageName == previous?.controller?.packageName ||
            (previous?.sourcePackage?.isNotEmpty()==true && it.packageName==previous.sourcePackage) ||
            (previous?.oemSource?.isNotEmpty()==true && it.packageName in setOf("com.qinggan.media", "com.qinggan.app.music", "com.qinggan.app.radio")) }
        val paused=memory.paused(controller, sourceChanged)
        val metadata=controller?.metadata
        // Artwork may arrive after pause. Do not discard that metadata callback or
        // borrow an image from a different track/source.
        if(!sourceChanged && metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)==paused.title){
            val cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val uri=firstArtworkUri(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
            return memory.remember(paused.copy(cover=cover ?: paused.cover,coverUri=uri.ifBlank{paused.coverUri}))
        }
        return paused
    }
}

/** Process RAM only: intentionally no preferences, files or saved-instance state. */
internal class LauncherMediaMemory {
    var last: LauncherMediaState? = null
        private set
    fun remember(state: LauncherMediaState): LauncherMediaState {
        last=state
        return state
    }
    fun paused(controller: MediaController?, sourceChanged: Boolean): LauncherMediaState {
        if(sourceChanged) last=null
        val previous=last ?: return LauncherMediaState()
        val position=controller?.playbackState?.position?.takeIf {it>=0} ?: previous.position
        return previous.copy(playing=false,controller=controller,position=if(previous.duration>0) position.coerceAtMost(previous.duration) else position)
            .also {last=it}
    }
}
