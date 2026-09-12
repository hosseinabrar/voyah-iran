package com.abrar.voyahiran.feature.steering.service

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
import com.abrar.voyahiran.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Keeps OEM interception alive outside our Activity; no screen-reading permission is needed. */
class StarBackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Unit>(Channel.CONFLATED)
    private var lastStartId = 0
    private var notificationMessage: Int? = null
    private val app get() = application as VoyahIranApplication

    override fun onCreate() {
        super.onCreate()
        com.abrar.voyahiran.core.logging.AppLog.info("StarService", "created")
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.star_back_label), NotificationManager.IMPORTANCE_LOW))
        app.starBackup.desired = app.starBackup.desired // Persist legacy preference before recovery can change the journal.
        showNotification(R.string.star_service_starting)
        scope.launch {
            commands.receive()
            while (isActive) {
                val startId = lastStartId
                val desired = app.starBackup.desired
                val ok = app.steeringController.setEnabled(desired)
                if (!app.starBackup.desired && app.starBackup.read() == null && ok) {
                    if (stopSelfResult(startId)) break
                }
                showNotification(if (ok && desired) R.string.star_service_active else R.string.star_service_starting)
                // Serial attempts: never overlap Binder writes or queue stale ON/OFF commands.
                withTimeoutOrNull(1000) { commands.receive() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        com.abrar.voyahiran.core.logging.AppLog.info("StarService", "action=${intent?.action ?: RESUME} startId=$startId desired=${app.starBackup.desired}")
        // Direct notification actions also use the same durable desired state.
        if (intent?.action == DISABLE) app.starBackup.desired = false
        if (intent?.action == ENABLE) app.starBackup.desired = true
        commands.trySend(Unit)
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        com.abrar.voyahiran.core.logging.AppLog.info("StarService", "destroyed")
        scope.cancel()
        commands.close()
        // Service/process destruction is not a user request to disable. Preserve intent and journal.
        super.onDestroy()
    }

    private fun showNotification(message: Int) {
        if (notificationMessage == message) return
        notificationMessage = message
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, StarBackService::class.java).setAction(DISABLE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(41, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.star_back_label))
            .setContentText(getString(message))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.star_restore), stop).build())
            .build())
    }

    companion object {
        const val ENABLE = "com.abrar.voyahiran.steering.ENABLE"
        const val DISABLE = "com.abrar.voyahiran.steering.DISABLE"
        const val RESUME = "com.abrar.voyahiran.steering.RESUME"
        private const val CHANNEL = "star_back"
        fun request(context: Context, action: String) {
            val backup = (context.applicationContext as VoyahIranApplication).starBackup
            when (action) { ENABLE -> backup.desired = true; DISABLE -> backup.desired = false }
            context.startForegroundService(Intent(context, StarBackService::class.java).setAction(RESUME))
        }
    }
}



