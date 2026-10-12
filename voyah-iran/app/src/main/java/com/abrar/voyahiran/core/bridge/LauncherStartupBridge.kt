package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object LauncherStartupBridge {
    fun open(): CommandResult {
        if(!BridgeSubscriptionSession.vipActive)return CommandResult(75,"Waiting for local VIP session")
        val modern=android.os.Build.VERSION.SDK_INT>=29
        val owner=Class.forName(if(modern)"android.app.ActivityTaskManager" else "android.app.ActivityManager")
        val type=Class.forName(if(modern)"android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val api=owner.getMethod("getService").invoke(null)
        val stacks=type.getMethod("getAllStackInfos").invoke(api) as List<*>
        val top=stacks.filterNotNull().firstOrNull {
            it.javaClass.getField("displayId").getInt(it)==0 && it.javaClass.getField("visible").getBoolean(it)
        }?.let {it.javaClass.getField("topActivity").get(it) as? ComponentName}
            ?: return CommandResult(75,"Central display not ready")
        // One startup attempt must not pull the user out of another app/settings.
        if(top.packageName!="com.qinggan.app.launcher")return CommandResult(0,"Initial HOME skipped: ${top.flattenToShortString()}")
        val result=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am","start","--user","0","--display","0",
            "--activity-new-task","--activity-clear-top","-n","com.abrar.voyahiran/.feature.launcher.VoyahLauncherActivity"),5)
        return if(result.exitCode==0 && result.output.contains("Error:"))CommandResult(1,result.output) else result
    }
}
