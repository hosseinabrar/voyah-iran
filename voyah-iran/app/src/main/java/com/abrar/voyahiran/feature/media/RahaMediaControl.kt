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
