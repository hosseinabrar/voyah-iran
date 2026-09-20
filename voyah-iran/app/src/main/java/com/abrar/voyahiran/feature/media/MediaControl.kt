package com.abrar.voyahiran.feature.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaControl {
    fun enabled(context: Context) = context.getSharedPreferences("media_controls", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun save(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences("media_controls", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).commit())
    }
    fun component(context: Context) = ComponentName(context, SteeringMediaListener::class.java)
    suspend fun provision(context: Context) {
        val enable = enabled(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.isNotificationListenerAccessGranted(component(context)) != enable) {
            val result = withContext(Dispatchers.IO) {
                ProcessCommandRunner().run(listOf("su", "0", "media-access", if (enable) "enable" else "disable"), 10)
            }
            check(!result.timedOut && result.exitCode == 0 &&
                manager.isNotificationListenerAccessGranted(component(context)) == enable) { "Media access setup failed: ${result.output}" }
        }
        if (enable) NotificationListenerService.requestRebind(component(context))
    }
}
