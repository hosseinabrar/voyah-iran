package com.abrar.voyahiran.core.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.feature.drivememory.service.DriveMemoryService
import com.abrar.voyahiran.feature.steering.service.StarBackService

class AppBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.abrar.voyahiran.core.logging.AppLog.info("Boot", "received=${intent.action}")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if ((context.applicationContext as VoyahIranApplication).starBackup.let { it.desired || it.read() != null }) {
            StarBackService.request(context, StarBackService.RESUME)
        }
        DriveMemoryService.request(context, if (intent.action == Intent.ACTION_BOOT_COMPLETED) DriveMemoryService.BOOT else DriveMemoryService.RESUME)
    }
}

