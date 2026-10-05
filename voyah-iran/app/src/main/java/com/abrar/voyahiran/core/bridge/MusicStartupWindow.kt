package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import android.os.Build
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object MusicStartupWindow {
    fun hide(packageName: String): CommandResult = try {
        val modern = Build.VERSION.SDK_INT >= 29
        val owner = if (modern) "android.app.ActivityTaskManager" else "android.app.ActivityManager"
        val api = Class.forName(if (modern) "android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val service = Class.forName(owner).getMethod("getService").invoke(null)
        val stack = api.getMethod("getFocusedStackInfo").invoke(service)
        val top = stack?.javaClass?.getField("topActivity")?.get(stack) as? ComponentName
        val display = stack?.javaClass?.getField("displayId")?.get(stack) as? Int
        if (display != 0 || top?.packageName != packageName) CommandResult(0, "UNCHANGED: player is not central foreground")
        else ProcessCommandRunner(useBridge = false).run(listOf("/system/bin/am", "start", "--user", "0",
            "--display", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"), 3)
    } catch (e: Exception) { CommandResult(1, "Music startup hide failed: ${e.message}") }
}
