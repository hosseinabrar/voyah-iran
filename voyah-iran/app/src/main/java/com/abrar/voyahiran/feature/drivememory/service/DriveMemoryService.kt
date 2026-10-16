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
    private data class Request(val action: String, val id: String, val queued: Long)
    private val commands = Channel<Request>(Channel.UNLIMITED)
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
            for (request in commands) {
                val action = request.action
                val started = android.os.SystemClock.elapsedRealtime()
                var success = false
                com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "request=${request.id} action=$action begin queueMs=${started - request.queued}")
                try {
                if(!(application as VoyahIranApplication).subscriptions.vipAllowed) { controller.detach();stopSelf();continue }
                if (action == BOOT) awaitBridgeAtBoot()
                val outcome = when (action) {
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
                    ENERGY_MODE_ENABLE -> controller.enableEnergyMode()
                    PEDESTRIAN_DISABLE -> controller.disablePedestrian()
                    ENERGY_MODE_DISABLE -> controller.disableEnergyMode()
                    PEDESTRIAN_RETRY -> controller.retryPedestrian()
                    ENERGY_MODE_RETRY -> controller.retryEnergyMode()
                    DOW_ENABLE -> controller.setDowEnabled(true)
                    DOW_DISABLE -> controller.setDowEnabled(false)
                    DOW_ALWAYS -> controller.setDowDoorOnly(false)
                    DOW_DOOR -> controller.setDowDoorOnly(true)
                    SHADE_ENABLE -> controller.setShadeEnabled(true)
                    SHADE_DISABLE -> controller.setShadeEnabled(false)
                    SHADE_READY_ENABLE -> controller.setShadeReadyEnabled(true)
                    SHADE_READY_DISABLE -> controller.setShadeReadyEnabled(false)
                    else -> controller.start()
                }
                success = outcome == true && !controller.state.value.error
                if (action == BOOT) com.abrar.voyahiran.core.logging.AppLog.info("Boot", "Background startup complete; UI not launched")
                if (!controller.hasActiveMemory) stopSelf()
                } catch (error: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    com.abrar.voyahiran.core.logging.AppLog.error("DriveService", "request=${request.id} timeout; not replayed; consumer continues", error)
                } catch (error: CancellationException) {
                    com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "request=${request.id} cancelled")
                    throw error
                } catch (error: Exception) {
                    com.abrar.voyahiran.core.logging.AppLog.error("DriveService", "request=${request.id} failed; not replayed; consumer continues", error)
                } finally {
                    controller.completeRequest(request.id, success)
                    com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "request=${request.id} action=$action end success=$success elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
                }
            }
        }
        scope.launch { while (isActive) {
            delay(1000)
            try { controller.tick() }
            catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                com.abrar.voyahiran.core.logging.AppLog.error("DriveService", "tick timed out; loop continues", error)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { com.abrar.voyahiran.core.logging.AppLog.error("DriveService", "tick failed; loop continues", error) }
        } }
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
        val id = intent?.getStringExtra("request_id") ?: java.util.UUID.randomUUID().toString()
        val queued = commands.trySend(Request(intent?.action ?: RESUME, id, android.os.SystemClock.elapsedRealtime()))
        com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "request=$id queued=${queued.isSuccess} startId=$startId")
        if (queued.isFailure) controller.completeRequest(id, false)
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        com.abrar.voyahiran.core.logging.AppLog.info("DriveService", "destroyed")
        commands.close(); scope.cancel()
        while (true) {
            val abandoned = commands.tryReceive().getOrNull() ?: break
            controller.completeRequest(abandoned.id, false)
        }
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
        const val ENERGY_MODE_ENABLE = "com.abrar.voyahiran.energyMode.ENABLE"
        const val PEDESTRIAN_DISABLE = "com.abrar.voyahiran.pedestrian.DISABLE"
        const val ENERGY_MODE_DISABLE = "com.abrar.voyahiran.energyMode.DISABLE"
        const val PEDESTRIAN_RETRY = "com.abrar.voyahiran.pedestrian.RETRY"
        const val ENERGY_MODE_RETRY = "com.abrar.voyahiran.energyMode.RETRY"
        const val DOW_ENABLE = "com.abrar.voyahiran.dow.ENABLE"
        const val DOW_DISABLE = "com.abrar.voyahiran.dow.DISABLE"
        const val DOW_ALWAYS = "com.abrar.voyahiran.dow.ALWAYS"
        const val DOW_DOOR = "com.abrar.voyahiran.dow.DOOR"
        const val SHADE_ENABLE = "com.abrar.voyahiran.shade.ENABLE"
        const val SHADE_DISABLE = "com.abrar.voyahiran.shade.DISABLE"
        const val SHADE_READY_ENABLE = "com.abrar.voyahiran.shade.READY_ENABLE"
        const val SHADE_READY_DISABLE = "com.abrar.voyahiran.shade.READY_DISABLE"
        private const val CHANNEL = "drive_memory"
        fun request(context: Context, action: String, requestId: String = java.util.UUID.randomUUID().toString()) {
            context.startForegroundService(Intent(context, DriveMemoryService::class.java).setAction(action).putExtra("request_id", requestId))
        }
    }
}
