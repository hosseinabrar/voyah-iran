package com.abrar.voyahiran.feature.media

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.view.KeyEvent
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
                history.edit().remove("attemptedBoot").commit()
                AppLog.info("MusicAutomation", "ACC off; next power-on re-armed")
            } else if (signal.value == 2) vehicleOff = false
        }
    }
    private var startup: Job? = null
    private var doors: Job? = null
    private var browser: MediaBrowser? = null
    private var browserController: MediaController? = null
    private var attempted = false
    private var bootLaunchPackage: String? = null

    fun tick() {
        val allowed = app.subscriptions.allowed
        if (!allowed || !preferences.musicAutoPlay) startup?.cancel()
        if (allowed && !vehicleOff && preferences.musicAutoPlay && preferences.musicPlayer.isNotBlank() && !attempted) {
            attempted = true
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            // Do not restart music on Activity recreation, listener rebind or app process restart.
            if (boot >= 0 && history.getInt("attemptedBoot", -2) != boot) {
                history.edit().putInt("attemptedBoot", boot).commit()
                startup = scope.launch { autoPlay(preferences.musicPlayer) }
            }
        }
        if (allowed && (preferences.musicPauseOnDoor || preferences.musicAutoPlay)) {
            if (doors == null || doors?.isCompleted == true) doors = scope.launch {
                try {
                    while (isActive && app.subscriptions.allowed && (preferences.musicPauseOnDoor || preferences.musicAutoPlay)) {
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
        val packageName = selection.substringBefore('#')
        try {
            AppLog.info("MusicAutomation", "boot start package=$packageName")
            if (selection != NavigationPreferences.OEM_RADIO && sessions().any { it.packageName == packageName && playing(it) }) return
            // A MediaBrowser service starts the application's playback process without any Activity.
            val services = context.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(packageName), 0)
            val oemService = when (selection) {
                NavigationPreferences.OEM_RADIO -> "com.qinggan.media.radio.LocalRadioBrowserService"
                NavigationPreferences.OEM_MUSIC -> "com.qinggan.media.usbmusic.LocalMediaBrowserService"
                else -> null
            }
            val service = if (oemService != null) {
                context.packageManager.getServiceInfo(ComponentName(packageName, oemService), 0).takeIf { it.exported }
            } else services.firstOrNull { it.serviceInfo.exported }?.serviceInfo
            if (service != null) {
                browser = MediaBrowser(context, ComponentName(packageName, service.name), object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        browser?.let { browserController = MediaController(context, it.sessionToken) }
                        AppLog.info("MusicAutomation", "browser connected package=$packageName")
                    }
                    override fun onConnectionFailed() { AppLog.info("MusicAutomation", "browser rejected package=$packageName") }
                    override fun onConnectionSuspended() { browserController = null }
                }, null).also { it.connect() }
            } else {
                check(oemService == null) { "OEM playback service unavailable: $oemService" }
                launchBehind(packageName)
            }
            delay(3000)
            var launched = service == null
            repeat(10) { attempt ->
                kotlin.coroutines.coroutineContext.ensureActive()
                if (!app.subscriptions.allowed || vehicleOff || !preferences.musicAutoPlay || preferences.musicPlayer != selection) return
                if (context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) return
                val controllers = sessions()
                if (controllers.any { it.packageName != packageName && playing(it) }) {
                    AppLog.info("MusicAutomation", "another player is active; leave user playback unchanged")
                    return
                }
                val target = browserController?.takeIf { it.packageName == packageName }
                    ?: controllers.firstOrNull { it.packageName == packageName && oemService == null }
                if (target != null && playing(target)) {
                    AppLog.info("MusicAutomation", "playing confirmed package=$packageName")
                    return
                }
                if (target != null) {
                    if (attempt >= 2 && !launched && oemService == null) {
                        launchBehind(packageName)
                        launched = true
                    }
                    target.transportControls.play()
                    AppLog.info("MusicAutomation", "play package=$packageName attempt=$attempt")
                } else {
                    if (oemService != null) { delay(3000); return@repeat }
                    if (!launched) { launchBehind(packageName); launched = true }
                    sendPlayButton(packageName)
                }
                delay(3000)
            }
            AppLog.info("MusicAutomation", "playback not confirmed package=$packageName; app may need login or a saved queue")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.error("MusicAutomation", "startup failed package=$packageName", e)
        } finally {
            browser?.disconnect(); browser = null; browserController = null
            val launchedPackage = bootLaunchPackage
            bootLaunchPackage = null
            if (launchedPackage != null) withContext(NonCancellable + Dispatchers.IO) {
                // A splash screen may replace its task and ignore launch-behind.
                // Hide only our startup player, never another foreground app.
                repeat(3) { attempt ->
                    val result = com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                        listOf("su", "0", "music-startup-hide", launchedPackage), 4)
                    AppLog.info("MusicAutomation", "hide startup player exit=${result.exitCode} detail=${result.output}")
                    if (result.exitCode != 75 || attempt == 2) return@withContext
                    delay(500)
                }
            }
        }
    }

    private fun launchBehind(packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: error("No launcher for $packageName")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(launch, ActivityOptions.makeTaskLaunchBehind().toBundle())
        bootLaunchPackage = packageName
        AppLog.info("MusicAutomation", "background launch requested package=$packageName")
    }

    private fun sendPlayButton(packageName: String) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
        val receiver = context.packageManager.queryBroadcastReceivers(intent, 0).firstOrNull { it.activityInfo.exported }?.activityInfo
        if (receiver == null) { AppLog.changed("MusicPlayReceiver", "no media receiver/session package=$packageName"); return }
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(Intent(intent).setComponent(ComponentName(packageName, receiver.name))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)))
        }
        AppLog.info("MusicAutomation", "targeted PLAY receiver=$packageName/${receiver.name}")
    }

    fun close() { startup?.cancel(); doors?.cancel(); browser?.disconnect(); browser = null; browserController = null }
}
