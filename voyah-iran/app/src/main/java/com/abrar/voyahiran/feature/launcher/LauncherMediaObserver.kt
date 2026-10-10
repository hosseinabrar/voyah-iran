package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.abrar.voyahiran.feature.media.MediaControl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One serialized reader, woken by session callbacks and the OEM publication. */
internal class LauncherMediaObserver(private val context: Context, private val scope: CoroutineScope,
    private val changed: (LauncherMediaState) -> Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(MediaSessionManager::class.java)
    private val controllers=mutableListOf<MediaController>()
    private val events=Channel<Unit>(Channel.CONFLATED)
    private var job: Job?=null
    private val callback=object: MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
        override fun onSessionDestroyed() { refresh() }
    }
    private val sessions=MediaSessionManager.OnActiveSessionsChangedListener { bind(it.orEmpty());refresh() }
    private val oem=object: ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private fun refresh() { events.trySend(Unit) }
    private fun bind(values: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } };controllers.clear()
        values.forEach { controller ->
            runCatching { controller.registerCallback(callback,handler);controllers.add(controller) }
        }
    }
    fun start() {
        stop()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor("voyah_iran_launcher_media"),false,oem)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessions,MediaControl.component(context),handler)
            bind(manager.getActiveSessions(MediaControl.component(context)))
        }.onFailure { com.abrar.voyahiran.core.logging.AppLog.error("LauncherMedia","session listener unavailable",it) }
        job=scope.launch {
            for(event in events) {
                val value=withContext(Dispatchers.IO) { runCatching { LauncherMedia.read(context) }.getOrNull() }
                if(value!=null)changed(value)
            }
        }
        refresh()
    }
    fun stop() {
        job?.cancel();job=null
        runCatching { context.contentResolver.unregisterContentObserver(oem) }
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        bind(emptyList())
    }
}
