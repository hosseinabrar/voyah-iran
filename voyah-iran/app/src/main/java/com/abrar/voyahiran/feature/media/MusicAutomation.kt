package com.abrar.voyahiran.feature.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.drivememory.data.CanDriveGateway
import com.abrar.voyahiran.feature.drivememory.data.CanField
import com.abrar.voyahiran.feature.navigation.NavigationPreferences
import kotlinx.coroutines.*

/** Owned by the media listener; never steals the foreground to warm a player. */
internal class MusicAutomation(private val context: Context, private val scope: CoroutineScope) {
    private val app get() = context.applicationContext as VoyahIranApplication
    private val preferences = NavigationPreferences(context)
    private val history = context.getSharedPreferences("music_boot_history", Context.MODE_PRIVATE)
    private val policy = MusicAutomationPolicy()
    private var vehicleOff = false
    private val gateway = CanDriveGateway(context) { signal ->
        if (signal.field == CanField.ACC) scope.launch {
            // Some head units sleep instead of rebooting. Re-arm on an observed ACC power cycle too.
            if (signal.value == 0 && !vehicleOff) {
                vehicleOff = true
                startup?.cancel()
                attempted = false
                withContext(Dispatchers.IO){history.edit().remove("attemptedBoot").commit()}
                AppLog.info("MusicAutomation", "ACC off; next power-on re-armed")
            } else if (signal.value == 2) vehicleOff = false
        }
    }
    private var startup: Job? = null
    private var doors: Job? = null
    private var attempted = false

    suspend fun tick() {
        val allowed = app.subscriptions.vipAllowed
        if (!allowed || !preferences.musicAutoPlay) startup?.cancel()
        if (allowed && !vehicleOff && preferences.musicAutoPlay && preferences.musicPlayer.isNotBlank() && !attempted) {
            attempted = true
            val boot = withContext(Dispatchers.IO){Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)}
            // Do not restart music on Activity recreation, listener rebind or app process restart.
            val claimed=withContext(Dispatchers.IO){
                boot>=0 && history.getInt("attemptedBoot",-2)!=boot &&
                    history.edit().putInt("attemptedBoot",boot).commit()
            }
            if (claimed && !vehicleOff && app.subscriptions.vipAllowed && preferences.musicAutoPlay) {
                startup = scope.launch { autoPlay(preferences.musicPlayer) }
            }
        }
        if (allowed && (preferences.musicPauseOnDoor || preferences.musicAutoPlay)) {
            if (doors == null || doors?.isCompleted == true) doors = scope.launch {
                try {
                    while (isActive && app.subscriptions.vipAllowed && (preferences.musicPauseOnDoor || preferences.musicAutoPlay)) {
                        try {
                            val door = withContext(Dispatchers.IO) { gateway.readDriverDoor() }
                            val opened = policy.doorOpened(door)
                            if (opened && preferences.musicPauseOnDoor) {
                                startup?.cancel() // A delayed Play must never undo the exit Pause.
                                AppLog.info("MusicAutomation", "driver door opened; cancelling startup and pausing active players")
                                sessions().filter { playing(it) }.forEach {
                                    try { it.transportControls.pause(); AppLog.info("MusicAutomation", "pause package=${it.packageName}") }
                                    catch (e: Exception) { AppLog.error("MusicAutomation", "pause failed package=${it.packageName}", e) }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            policy.resetDoor()
                            AppLog.changed("MusicDoor", "read failed: ${e.message}")
                            delay(2000)
                        }
                        delay(250)
                    }
                } finally { withContext(NonCancellable + Dispatchers.IO) { gateway.close() }; policy.resetDoor() }
            }
        } else { doors?.cancel() }
    }

    private fun sessions(): List<MediaController> = context.getSystemService(MediaSessionManager::class.java)
        .getActiveSessions(MediaControl.component(context))
    private fun playing(controller: MediaController) = controller.playbackState?.state in listOf(
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)

    private suspend fun autoPlay(selection: String) {
        try {
            RahaMediaControl.playDefault(context,0) {
                app.subscriptions.vipAllowed && !vehicleOff && preferences.musicAutoPlay &&
                    preferences.musicPlayer == selection &&
                    context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL
            }
        } catch(e:Exception) {
            if(e is CancellationException) throw e
            AppLog.error("MusicAutomation","startup failed",e)
        }
    }

    fun close() { startup?.cancel(); doors?.cancel() }
}
