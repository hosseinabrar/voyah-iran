package com.abrar.voyahiran.feature.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult

/** Use a real application identity; the OEM media shell dispatch supplies an empty package. */
object RahaMediaControl {
    /** Explicit empty-card play: address the selected player, never a stale global recipient. */
    suspend fun playDefault(context: Context) {
        val prefs=com.abrar.voyahiran.feature.navigation.NavigationPreferences(context)
        val selection=prefs.musicPlayer
        val pkg=selection.substringBefore('#')
        val manager=context.getSystemService(MediaSessionManager::class.java)
        fun session()=runCatching { manager.getActiveSessions(MediaControl.component(context)) }
            .getOrDefault(emptyList()).firstOrNull { it.packageName==pkg }
        val radio=selection==com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_RADIO
        if(!radio) session()?.let { it.transportControls.play();return }
        val serviceName=when(selection) {
            com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_RADIO -> "com.qinggan.media.radio.LocalRadioBrowserService"
            com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_MUSIC -> "com.qinggan.media.usbmusic.LocalMediaBrowserService"
            else -> context.packageManager.queryIntentServices(android.content.Intent("android.media.browse.MediaBrowserService").setPackage(pkg),0)
                .firstOrNull { it.serviceInfo.exported }?.serviceInfo?.name
        }
        if(serviceName!=null) {
            val ready=kotlinx.coroutines.CompletableDeferred<MediaController?>()
            var browser: android.media.browse.MediaBrowser?=null
            try {
                browser=android.media.browse.MediaBrowser(context,android.content.ComponentName(pkg,serviceName),
                    object:android.media.browse.MediaBrowser.ConnectionCallback() {
                        override fun onConnected() { ready.complete(browser?.let { MediaController(context,it.sessionToken) }) }
                        override fun onConnectionFailed() { ready.complete(null) }
                        override fun onConnectionSuspended() { ready.complete(null) }
                    },null)
                browser.connect()
                val target=kotlinx.coroutines.withTimeoutOrNull(5000) { ready.await() }
                if(target!=null) { target.transportControls.play();return }
            } finally { browser?.disconnect() }
        }
        com.abrar.voyahiran.feature.navigation.DefaultAppLaunch.execute(context,
            if(radio) "OPEN_OEM_RADIO" else "OPEN_DEFAULT_MUSIC",null)
        val target=kotlinx.coroutines.withTimeoutOrNull(8000) {
            var found=session()
            while(found==null) { kotlinx.coroutines.delay(200);found=session() }
            found
        }
        checkNotNull(target) { "Default player did not expose a playback session" }.transportControls.play()
    }
    val actions = setOf("PLAY_MEDIA", "PAUSE_MEDIA", "NEXT_MEDIA", "PREVIOUS_MEDIA")
    fun execute(context: Context, action: String): CommandResult {
        require(action in actions)
        return try {
            val sessions = try {
                context.getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaControl.component(context))
            } catch (_: SecurityException) { emptyList<MediaController>() }
            val factory = com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_MUSIC
            val factorySelected = com.abrar.voyahiran.feature.navigation.NavigationPreferences(context).musicPlayer == factory
            // A paused third-party session must not steal commands from the selected OEM player.
            val candidates = if (factorySelected) sessions.filter { it.packageName == factory } else sessions
            val target = candidates.firstOrNull { it.playbackState?.state in listOf(
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING) }
                ?: candidates.firstOrNull { it.playbackState?.state in listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED) }
            if (target != null) {
                val controls = target.transportControls
                when (action) {
                    "PLAY_MEDIA" -> controls.play()
                    "PAUSE_MEDIA" -> controls.pause()
                    "NEXT_MEDIA" -> controls.skipToNext()
                    "PREVIOUS_MEDIA" -> controls.skipToPrevious()
                }
                AppLog.info("RahaMedia", "action=$action package=${target.packageName} transport dispatched")
            } else {
                // No addressable session: let Android select its current media-button recipient.
                // This is an alternative before dispatch, never a replay after an uncertain result.
                val key = when(action) { "PLAY_MEDIA" -> 126; "PAUSE_MEDIA" -> 127; "NEXT_MEDIA" -> 87; else -> 88 }
                val audio = context.getSystemService(AudioManager::class.java)
                val now = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
                AppLog.info("RahaMedia", "action=$action package=${context.packageName} system media-key dispatched")
            }
            CommandResult(0, "Media command dispatched: $action")
        } catch(e: Exception) {
            AppLog.error("RahaMedia", "action=$action failed; not replayed", e)
            CommandResult(1, "Media dispatch failed: ${e.message}")
        }
    }
}
