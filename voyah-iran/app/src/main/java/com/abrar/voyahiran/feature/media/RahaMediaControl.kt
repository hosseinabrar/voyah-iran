package com.abrar.voyahiran.feature.media

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult

/** Use a real application identity; the OEM media shell dispatch supplies an empty package. */
object RahaMediaControl {
    private val defaultPlayMutex = kotlinx.coroutines.sync.Mutex()
    private fun playing(controller: MediaController) = controller.playbackState?.state in listOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)

    /** Shared empty-source startup; never addresses a stale global media recipient. */
    suspend fun playDefault(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
        if(!defaultPlayMutex.tryLock())return@withContext
        var browser: android.media.browse.MediaBrowser?=null
        try {
            val prefs=com.abrar.voyahiran.feature.navigation.NavigationPreferences(context)
            val selection=prefs.musicPlayer
            val pkg=selection.substringBefore('#')
            val factory=selection in setOf(com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_RADIO,
                com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_MUSIC)
            val manager=context.getSystemService(MediaSessionManager::class.java)
            fun sessions()=manager.getActiveSessions(MediaControl.component(context))
            if(sessions().any {it.packageName!=pkg && playing(it)})return@withContext
            var target:MediaController?=if(factory)null else sessions().firstOrNull {it.packageName==pkg}
            if(target?.let(::playing)==true)return@withContext
            // Clear force-stop and initialize the application's playback queue.
            if(!factory)com.abrar.voyahiran.feature.navigation.DefaultAppLaunch.execute(context,"OPEN_DEFAULT_MUSIC",null)
            val serviceName=when(selection) {
                com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_RADIO -> "com.qinggan.media.radio.LocalRadioBrowserService"
                com.abrar.voyahiran.feature.navigation.NavigationPreferences.OEM_MUSIC -> "com.qinggan.media.usbmusic.LocalMediaBrowserService"
                else -> context.packageManager.queryIntentServices(android.content.Intent("android.media.browse.MediaBrowserService").setPackage(pkg),0)
                    .firstOrNull {it.serviceInfo.exported}?.serviceInfo?.name
            }
            if(serviceName!=null) {
                browser=android.media.browse.MediaBrowser(context,android.content.ComponentName(pkg,serviceName),
                    object:android.media.browse.MediaBrowser.ConnectionCallback() {
                        override fun onConnected() { target=browser?.let {MediaController(context,it.sessionToken)} }
                        override fun onConnectionSuspended() {target=null}
                    },null)
                browser.connect()
            }
            repeat(12) { attempt ->
                check(prefs.musicPlayer==selection) {"Default player changed during startup"}
                val current=sessions()
                if(current.any {it.packageName!=pkg && playing(it)})return@withContext
                if(!factory)target=current.firstOrNull {it.packageName==pkg} ?: target
                if(target?.playbackState?.state==PlaybackState.STATE_PLAYING) {
                    AppLog.info("RahaMedia","default playback confirmed package=$pkg")
                    return@withContext
                }
                target?.transportControls?.play()
                AppLog.info("RahaMedia","default play package=$pkg attempt=$attempt session=${target!=null}")
                kotlinx.coroutines.delay(1000)
            }
            check(target?.playbackState?.state==PlaybackState.STATE_PLAYING) {"Default player did not start playback"}
        } finally {
            browser?.disconnect()
            defaultPlayMutex.unlock()
        }
    }
    val actions = setOf("PLAY_MEDIA", "PAUSE_MEDIA", "NEXT_MEDIA", "PREVIOUS_MEDIA")
    suspend fun execute(context: Context, action: String): CommandResult {
        require(action in actions)
        return try {
            val sessions = try {
                context.getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaControl.component(context))
            } catch (_: SecurityException) { emptyList<MediaController>() }
            val target = sessions.firstOrNull(::playing)
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
                if(action != "PAUSE_MEDIA")playDefault(context)
            }
            CommandResult(0, "Media command dispatched: $action")
        } catch(e: Exception) {
            if(e is kotlinx.coroutines.CancellationException)throw e
            AppLog.error("RahaMedia", "action=$action failed; not replayed", e)
            CommandResult(1, "Media dispatch failed: ${e.message}")
        }
    }
}
