package com.abrar.voyahiran.feature.media

import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*

/** Uses notification-listener authorization only for MediaSession access; never reads notification content. */
class SteeringMediaListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val app get() = application as VoyahIranApplication
    private data class Destination(val controller:MediaController? = null)
    private val presses = MediaPressPolicy<Destination>()
    @Volatile private var defaultNeeded = false
    private var defaultPlay:Job?=null
    @Volatile private var target: MediaController? = null
    @Volatile private var connected = false
    @Volatile private var usable = false
    private var last: MediaSession.Token? = null
    private var monitor: Job? = null
    private val clusterMusic by lazy { ClusterMusicPublisher(this, scope) }
    private val automation by lazy { MusicAutomation(this, scope) }
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        clusterMusic.start()
        com.abrar.voyahiran.feature.subscription.SubscriptionService.start(this)
        monitor?.cancel()
        monitor = scope.launch {
            while (isActive && connected) {
                try {
                    automation.tick()
                    usable = MediaControl.interceptEnabled(this@SteeringMediaListener) && app.subscriptions.vipAllowed &&
                        getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL
                    if (usable) {
                        val sessions = getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaControl.component(this@SteeringMediaListener))
                        val choices = sessions.map { c ->
                            val state = c.playbackState?.state
                            MediaRoutingPolicy.Session(c.sessionToken,
                                state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING),
                                MediaRoutingPolicy.resumable(state == PlaybackState.STATE_PAUSED,
                                    MediaRoutingPolicy.factorySource(c.packageName),c.sessionToken==last))
                        }
                        val chosen = MediaRoutingPolicy.choose(choices, last)
                        val selected = sessions.firstOrNull { it.sessionToken == chosen }
                        // Select among ALL sources first, then leave factory sources to OEM.
                        // Filtering them before selection would incorrectly resume a paused user app.
                        target = selected?.takeUnless { MediaRoutingPolicy.factorySource(it.packageName) }
                        defaultNeeded = selected==null
                        if (chosen != null) last = chosen
                        AppLog.changed("SteeringMedia", "target=${target?.packageName} selected=${selected?.packageName} native=${selected!=null && target==null} state=${selected?.playbackState?.state}")
                        app.steeringKeys.setMediaListener(::onKey)
                    } else {
                        target = null
                        presses.reset()
                        app.steeringKeys.setMediaListener(null)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    target = null
                    presses.reset()
                    AppLog.changed("SteeringMedia", "retry=${e.javaClass.simpleName}: ${e.message}")
                }
                delay(500)
            }
        }
        AppLog.info("SteeringMedia", "listener connected")
    }
    private fun onKey(key: Int, action: Int, display: Int): Boolean {
        val code = MediaRoutingPolicy.androidKey(key) ?: return false
        if (!connected || !usable || !MediaControl.interceptEnabled(this) || !app.subscriptions.vipAllowed) {
            AppLog.info("SteeringMedia", "ignored key=$key action=$action display=$display connected=$connected usable=$usable enabled=${MediaControl.interceptEnabled(this)} subscription=${app.subscriptions.vipAllowed}")
            return false
        }
        val destination=target?.let {Destination(it)} ?: if(defaultNeeded && key!=9)Destination() else null
        val route = presses.route(key, action, destination, display)
        AppLog.info("SteeringMedia", "key=$key action=$action display=$display consumed=${route.consumed} target=${route.target?.controller?.packageName} default=${route.target!=null && route.target.controller==null}")
        route.target?.let { destination ->
            // Never call another Binder synchronously while replying to the OEM key engine.
            handler.post {
                if (!connected || !usable || !MediaControl.interceptEnabled(this) || !app.subscriptions.vipAllowed) return@post
                val controller=destination.controller
                if(controller==null) {
                    if(defaultPlay?.isActive!=true)defaultPlay=scope.launch {
                        try {RahaMediaControl.playDefault(this@SteeringMediaListener)}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){AppLog.error("SteeringMedia","default play failed",e)}
                    }
                    return@post
                }
                try {
                    val now = SystemClock.uptimeMillis()
                    val down = controller.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                    val up = controller.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
                    AppLog.info("SteeringMedia", "dispatch package=${controller.packageName} androidKey=$code down=$down up=$up")
                } catch (e: Exception) { AppLog.error("SteeringMedia", "dispatch failed package=${controller.packageName}", e) }
            }
        }
        return route.consumed
    }
    private fun detach(destroy: Boolean) {
        clusterMusic.stop()
        automation.close()
        connected = false; usable = false; target = null
        defaultNeeded=false;defaultPlay?.cancel()
        monitor?.cancel(); presses.reset(); handler.removeCallbacksAndMessages(null)
        scope.launch {
            try { app.steeringKeys.setMediaListener(null) }
            catch (e: Exception) { AppLog.error("SteeringMedia", "release failed", e) }
            finally { if (destroy) scope.cancel() }
        }
    }
    override fun onListenerDisconnected() {
        detach(false)
        super.onListenerDisconnected()
        if (MediaControl.accessNeeded(this)) requestRebind(MediaControl.component(this))
    }
    override fun onDestroy() { detach(true); super.onDestroy() }
}
