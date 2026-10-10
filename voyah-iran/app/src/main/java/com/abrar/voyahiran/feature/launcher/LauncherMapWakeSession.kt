package com.abrar.voyahiran.feature.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/** Process-wide: activity recreation and ordinary HOME returns are not new driving sessions. */
internal object LauncherMapWakeSession {
    private var registered = false
    var generation = 1L; private set
    var handled = 0L
    private var awake = true
    var restarting = false
    val pending get() = awake && handled != generation
    fun observe(context: Context) {
        if (registered) return
        awake = (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
        context.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> { awake = false; generation++ }
                    Intent.ACTION_SCREEN_ON -> awake = true
                }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
        registered = true
    }
}
