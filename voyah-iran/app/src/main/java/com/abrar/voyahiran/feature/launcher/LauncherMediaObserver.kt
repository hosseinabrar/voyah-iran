package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.feature.media.MediaControl
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Stable token subscriptions; OEM publications also recover access granted after HOME starts. */
internal class LauncherMediaObserver(private val context: Context, private val scope: CoroutineScope,
    private val changed: (LauncherMediaState) -> Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(MediaSessionManager::class.java)
    private val controllers=mutableMapOf<MediaSession.Token,Pair<MediaController,MediaController.Callback>>()
    private data class Event(val reason:String,val at:Long=SystemClock.elapsedRealtime())
    private val events=Channel<Event>(Channel.CONFLATED)
    private var job: Job?=null
    private var running=false
    private var sessionListenerRegistered=false
    private var lastError=""
    private val sessions=MediaSessionManager.OnActiveSessionsChangedListener {
        if(running) { bind(it.orEmpty());refresh("sessions") }
    }
    private val oem=object: ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh("oem") }
    }
    private fun refresh(reason:String) { if(running) events.trySend(Event(reason)) }
    private fun failed(operation:String,error:Throwable) {
        val signature=operation+":"+error.toString()
        if(signature!=lastError) {lastError=signature;AppLog.error("LauncherMedia",operation,error)}
    }
    private fun bind(values: List<MediaController>) {
        val tokens=values.map { it.sessionToken }.toSet()
        controllers.keys.filter { it !in tokens }.forEach { token ->
            controllers.remove(token)?.let { (c,cb)->runCatching { c.unregisterCallback(cb) } }
        }
        values.forEach { controller ->
            val token=controller.sessionToken
            if(token !in controllers) {
                val callback=object:MediaController.Callback() {
                    override fun onPlaybackStateChanged(state:PlaybackState?) {
                        if(!running)return
                        AppLog.info("LauncherMedia","callback pkg=${controller.packageName} state=${state?.state} position=${state?.position} at=${SystemClock.elapsedRealtime()}")
                        refresh("playback")
                    }
                    override fun onMetadataChanged(metadata:MediaMetadata?) { refresh("metadata") }
                    override fun onSessionDestroyed() {
                        controllers.remove(token)?.let { (c,cb)->runCatching { c.unregisterCallback(cb) } }
                        refresh("destroyed")
                    }
                }
                runCatching {
                    controller.registerCallback(callback,handler)
                    controllers[token]=controller to callback
                    AppLog.info("LauncherMedia","bound pkg=${controller.packageName}")
                }.onFailure { failed("callback registration pkg=${controller.packageName}",it) }
            }
        }
    }
    private fun registerSessions() {
        if(sessionListenerRegistered)return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessions,MediaControl.component(context),handler)
            sessionListenerRegistered=true
        }.onFailure { failed("session listener unavailable",it) }
    }
    fun start() {
        stop();running=true
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor("voyah_iran_launcher_media"),false,oem)
        registerSessions()
        job=scope.launch {
            for(event in events) {
                registerSessions()
                val snapshot=withContext(Dispatchers.IO) {
                    runCatching { manager.getActiveSessions(MediaControl.component(context)) }
                }
                snapshot.onSuccess { bind(it) }.onFailure { failed("session snapshot unavailable",it) }
                val value=withContext(Dispatchers.IO) {
                    runCatching { LauncherMedia.read(context,snapshot.getOrDefault(emptyList())) }
                }
                value.onSuccess {
                    changed(it)
                    AppLog.info("LauncherMedia","applied reason=${event.reason} pkg=${it.sourcePackage} source=${it.oemSource} playing=${it.playing} position=${it.position} delayMs=${SystemClock.elapsedRealtime()-event.at}")
                }.onFailure { failed("media read failed",it) }
            }
        }
        refresh("start")
    }
    fun stop() {
        running=false;job?.cancel();job=null
        runCatching { context.contentResolver.unregisterContentObserver(oem) }
        if(sessionListenerRegistered)runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        sessionListenerRegistered=false
        bind(emptyList())
        while(events.tryReceive().isSuccess) { }
    }
}
