package com.abrar.voyahiran.feature.media

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Use a real application identity; the OEM media shell dispatch supplies an empty package. */
object RahaMediaControl {
    private val defaultPlayMutex = kotlinx.coroutines.sync.Mutex()
    private fun playing(controller: MediaController) = controller.playbackState?.state in listOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)

    /** Shared empty-source startup; never addresses a stale global media recipient. */
    suspend fun playDefault(context: Context, displayId:Int=0, allowed:()->Boolean={true}) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
        require(displayId in 0..1)
        if(!defaultPlayMutex.tryLock())return@withContext
        var browser: android.media.browse.MediaBrowser?=null
        var windowToken:String?=null
        var windowJob:kotlinx.coroutines.Job?=null
        var launched=false
        suspend fun window(action:String, token:String):CommandResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(listOf("su","0","music-launch-window",action,token),3)
        }
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
            if(!allowed())return@withContext
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
                if(!allowed())return@withContext
                check(prefs.musicPlayer==selection) {"Default player changed during startup"}
                val current=sessions()
                if(current.any {it.packageName!=pkg && playing(it)})return@withContext
                if(!factory)target=current.firstOrNull {it.packageName==pkg} ?: target
                if(target?.playbackState?.state==PlaybackState.STATE_PLAYING) {
                    AppLog.info("RahaMedia","default playback confirmed package=$pkg")
                    return@withContext
                }
                target?.transportControls?.play()
                if(target==null && !factory) {
                    val intent=android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                    val receiver=context.packageManager.queryBroadcastReceivers(intent,0).firstOrNull {it.activityInfo.exported}?.activityInfo
                    if(receiver!=null) for(action in listOf(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.ACTION_UP)) {
                        context.sendBroadcast(android.content.Intent(intent).setComponent(android.content.ComponentName(pkg,receiver.name))
                            .putExtra(android.content.Intent.EXTRA_KEY_EVENT,android.view.KeyEvent(action,android.view.KeyEvent.KEYCODE_MEDIA_PLAY)))
                    }
                }
                // Give the session/browser a chance before an Activity fallback.
                if(attempt>=2 && !factory && !launched && target?.let(::playing)!=true) {
                    launched=true
                    val prepared=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(listOf("su","0","music-launch-window","begin",pkg,displayId.toString()),3)
                    }
                    windowToken=prepared.output.lineSequence().firstOrNull {it.startsWith("TOKEN=")}?.removePrefix("TOKEN=")
                    AppLog.info("RahaMedia","background prepare display=$displayId exit=${prepared.exitCode} ${prepared.output}")
                    if(prepared.exitCode==0 && windowToken!=null) {
                        val intent=context.packageManager.getLaunchIntentForPackage(pkg) ?: error("No launcher for $pkg")
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        val options=android.app.ActivityOptions.makeTaskLaunchBehind().apply {setLaunchDisplayId(displayId)}
                        windowJob=kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch {
                            while(kotlinx.coroutines.currentCoroutineContext().isActive) {
                                val result=window("hide",windowToken!!)
                                AppLog.changed("MusicLaunchWindow","display=$displayId exit=${result.exitCode} ${result.output}")
                                if(result.output.startsWith("DONE"))break
                                kotlinx.coroutines.delay(300)
                            }
                        }
                        context.startActivity(intent,options.toBundle())
                    }
                }
                AppLog.info("RahaMedia","default play package=$pkg attempt=$attempt session=${target!=null}")
                kotlinx.coroutines.delay(1000)
            }
            check(target?.playbackState?.state==PlaybackState.STATE_PLAYING) {"Default player did not start playback"}
        } finally {
            try {
                windowJob?.cancel()
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    windowJob?.join()
                    windowToken?.let { token ->
                        try {
                            // Splash -> content replacement may happen after playback confirmation.
                            repeat(8) {
                                val result=window("hide",token)
                                AppLog.changed("MusicLaunchWindow","cleanup exit=${result.exitCode} ${result.output}")
                                if(result.output.startsWith("DONE"))return@let
                                kotlinx.coroutines.delay(300)
                            }
                        } finally { window("end",token) }
                    }
                }
            } finally { try { browser?.disconnect() } finally { defaultPlayMutex.unlock() } }
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
