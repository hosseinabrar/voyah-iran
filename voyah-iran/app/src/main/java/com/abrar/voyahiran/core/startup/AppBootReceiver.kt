package com.abrar.voyahiran.core.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.abrar.voyahiran.core.logging.AppLog.info("Boot", "received=${intent.action}")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        com.abrar.voyahiran.feature.subscription.SubscriptionService.start(context)
    }
}
