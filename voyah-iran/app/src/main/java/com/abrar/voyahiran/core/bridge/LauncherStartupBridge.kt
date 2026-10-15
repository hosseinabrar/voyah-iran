package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object LauncherStartupBridge {
    private val handledDisplays = mutableSetOf<Int>()
    @Synchronized fun open(): CommandResult {
        if(!BridgeSubscriptionSession.vipActive)return CommandResult(75,"Waiting for local VIP session")
        val modern=android.os.Build.VERSION.SDK_INT>=29
        val owner=Class.forName(if(modern)"android.app.ActivityTaskManager" else "android.app.ActivityManager")
        val type=Class.forName(if(modern)"android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val api=owner.getMethod("getService").invoke(null)
        val stacks=type.getMethod("getAllStackInfos").invoke(api) as List<*>
        val reports=mutableListOf<String>()
        // Each display completes independently. Retrying a late passenger display
        // must not reopen the central HOME after the user has already left it.
        for(display in listOf(0,1)) {
            if(display in handledDisplays)continue
            val top=stacks.filterNotNull().firstOrNull {
                it.javaClass.getField("displayId").getInt(it)==display && it.javaClass.getField("visible").getBoolean(it)
            }?.let {it.javaClass.getField("topActivity").get(it) as? ComponentName}
            if(top==null) {reports.add("display=$display not ready");continue}
            // Initial startup must not pull either screen out of another app/settings.
            if(top.packageName!="com.qinggan.app.launcher") {
                handledDisplays.add(display)
                reports.add("display=$display skipped: ${top.flattenToShortString()}")
                continue
            }
            val activity=if(display==0)"VoyahLauncherActivity" else "PassengerLauncherActivity"
            val result=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am","start","--user","0","--display",display.toString(),
                "--activity-new-task","--activity-clear-top","-n","com.abrar.voyahiran/.feature.launcher.$activity"),2)
            if(result.exitCode==0 && !result.timedOut && !result.output.contains("Error:"))handledDisplays.add(display)
            reports.add("display=$display ${result.output}")
        }
        return CommandResult(if(handledDisplays.containsAll(listOf(0,1)))0 else 75,reports.joinToString("\n"))
    }
}
