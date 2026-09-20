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
    private val presses = MediaPressPolicy<MediaController>()
    @Volatile private var target: MediaController? = null
    @Volatile private var connected = false
    @Volatile private var usable = false
    private var last: MediaSession.Token? = null
    private var monitor: Job? = null
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        com.abrar.voyahiran.feature.subscription.SubscriptionService.start(this)
        monitor?.cancel()
        monitor = scope.launch {
            while (isActive && connected) {
                try {
                    usable = MediaControl.enabled(this@SteeringMediaListener) && app.subscriptions.allowed &&
                        getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL
                    if (usable) {
                        val sessions = getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaControl.component(this@SteeringMediaListener))
                        val choices = sessions.map { c ->
                            val state = c.playbackState?.state
                            MediaRoutingPolicy.Session(c.sessionToken,
                                state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING),
                                state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED)
                        }
                        val chosen = MediaRoutingPolicy.choose(choices, last)
                        target = sessions.firstOrNull { it.sessionToken == chosen }
                        if (chosen != null) last = chosen
                        AppLog.changed("SteeringMedia", "target=${target?.packageName} state=${target?.playbackState?.state}")
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
        if (!connected || !usable || display != 0 || !MediaControl.enabled(this) || !app.subscriptions.allowed) return false
        val code = MediaRoutingPolicy.androidKey(key) ?: return false
        val route = presses.route(key, action, target)
        AppLog.info("SteeringMedia", "key=$key action=$action display=$display consumed=${route.consumed} target=${route.target?.packageName}")
        route.target?.let { controller ->
            // Never call another Binder synchronously while replying to the OEM key engine.
            handler.post {
                if (!connected || !usable || !MediaControl.enabled(this) || !app.subscriptions.allowed) return@post
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
        connected = false; usable = false; target = null
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
        if (MediaControl.enabled(this)) requestRebind(MediaControl.component(this))
    }
    override fun onDestroy() { detach(true); super.onDestroy() }
}
