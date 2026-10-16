package com.abrar.voyahiran.feature.drivememory.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.abrar.voyahiran.R
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class DriveMemoryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<String>(Channel.UNLIMITED)
    private val controller get() = (application as VoyahIranApplication).driveMemoryController

    override fun onCreate() {
        super.onCreate()
        com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "created")
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.comfort_tab), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(42, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.drive_notification))
            .setContentIntent(open).setOngoing(true).build())
        scope.launch {
            for (action in commands) {
                if(!(application as VoyahIranApplication).subscriptions.vipAllowed) { controller.detach();stopSelf();break }
                if (action == BOOT) awaitBridgeAtBoot()
                when (action) {
                    ENABLE -> controller.enable()
                    DISABLE -> controller.disable()
                    RETRY -> controller.retry()
                    RECOVERY_ENABLE -> controller.enableRecovery()
                    RECOVERY_DISABLE -> controller.disableRecovery()
                    RECOVERY_RETRY -> controller.retryRecovery()
                    AEB_ENABLE -> controller.enableAeb()
                    AEB_DISABLE -> controller.disableAeb()
                    AEB_RETRY -> controller.retryAeb()
                    PEDESTRIAN_ENABLE -> controller.enablePedestrian()
                    PEDESTRIAN_DISABLE -> controller.disablePedestrian()
                    PEDESTRIAN_RETRY -> controller.retryPedestrian()
                    else -> controller.start()
                }
                if (action == BOOT) com.abrar.voyahiran.core.logging.AppLog.info("Boot", "Background startup complete; UI not launched")
                if (!controller.hasActiveMemory) stopSelf()
            }
        }
        scope.launch { while (isActive) { delay(1000); controller.tick() } }
    }

    private suspend fun awaitBridgeAtBoot() = withContext(Dispatchers.IO) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        var ready = false
        while (isActive && android.os.SystemClock.elapsedRealtime() < deadline) {
            // Only the read-only handshake is retried, never a vehicle command.
            val result = ProcessCommandRunner().run(listOf("su", "0", "id", "-u"), 1)
            ready = !result.timedOut && result.exitCode == 0 && result.output.trim() == "0"
            if (ready) break
            delay(500)
        }
        com.abrar.voyahiran.core.logging.AppLog.info("Boot", "bridge-ready=$ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "action=${intent?.action ?: RESUME} startId=$startId")
        commands.trySend(intent?.action ?: RESUME)
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "destroyed")
        commands.close(); scope.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { controller.detach() }
        super.onDestroy()
    }

    companion object {
        const val ENABLE = "com.abrar.voyahiran.drive.ENABLE"
        const val DISABLE = "com.abrar.voyahiran.drive.DISABLE"
        const val RESUME = "com.abrar.voyahiran.drive.RESUME"
        const val RETRY = "com.abrar.voyahiran.drive.RETRY"
        const val BOOT = "com.abrar.voyahiran.drive.BOOT"
        const val RECOVERY_ENABLE = "com.abrar.voyahiran.recovery.ENABLE"
        const val RECOVERY_DISABLE = "com.abrar.voyahiran.recovery.DISABLE"
        const val RECOVERY_RETRY = "com.abrar.voyahiran.recovery.RETRY"
        const val AEB_ENABLE = "com.abrar.voyahiran.aeb.ENABLE"
        const val AEB_DISABLE = "com.abrar.voyahiran.aeb.DISABLE"
        const val AEB_RETRY = "com.abrar.voyahiran.aeb.RETRY"
        const val PEDESTRIAN_ENABLE = "com.abrar.voyahiran.pedestrian.ENABLE"
        const val PEDESTRIAN_DISABLE = "com.abrar.voyahiran.pedestrian.DISABLE"
        const val PEDESTRIAN_RETRY = "com.abrar.voyahiran.pedestrian.RETRY"
        private const val CHANNEL = "drive_memory"
        fun request(context: Context, action: String) {
            context.startForegroundService(Intent(context, DriveMemoryService::class.java).setAction(action))
        }
    }
}
