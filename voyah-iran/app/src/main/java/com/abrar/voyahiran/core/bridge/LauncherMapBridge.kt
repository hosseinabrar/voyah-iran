package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

/** Moves only the selected map's normal activity stack; never moves a vehicle/system stack. */
internal object LauncherMapBridge {
    fun execute(args: List<String>): CommandResult = try {
        check(BridgeSubscriptionSession.active) { "Subscription inactive" }
        val pkg = args[1]
        val destination = args[2].toInt()
        if (destination != 0) {
            val global = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val info = global.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(instance, destination)
            check(info != null && info.javaClass.getField("name").get(info) == "VoyahIran.MapPreview") { "Not a map preview display" }
            check(info.javaClass.getField("ownerPackageName").get(info) == "com.abrar.voyahiran") { "Wrong display owner" }
        }
        val owner = if (android.os.Build.VERSION.SDK_INT >= 29) "android.app.ActivityTaskManager" else "android.app.ActivityManager"
        val apiClass = Class.forName(if (android.os.Build.VERSION.SDK_INT >= 29) "android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val api = Class.forName(owner).getMethod("getService").invoke(null)
        val stacks = apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>
        fun containsMap(stack: Any): Boolean {
            val top=(stack.javaClass.getField("topActivity").get(stack) as? ComponentName)?.packageName
            if(top==pkg) return true
            val names=runCatching { stack.javaClass.getField("taskNames").get(stack) as Array<*> }.getOrNull()
            return names?.any { name -> name?.toString()?.let { it==pkg || it.startsWith("$pkg/") } == true } == true
        }
        val candidates=stacks.filterNotNull().filter(::containsMap)
        // Prefer passenger ownership, including a background task, over starting another copy.
        val stack = candidates.firstOrNull { it.javaClass.getField("displayId").getInt(it)==1 } ?: candidates.firstOrNull()
        if(args[0]=="launcher-map-status") {
            val current=stack?.let { it.javaClass.getField("displayId").getInt(it) } ?: -1
            val top=stack?.let { (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName==pkg } == true
            val visible=stack?.let { runCatching { it.javaClass.getField("visible").getBoolean(it) }.getOrDefault(top) } == true
            CommandResult(0,"MAP_DISPLAY=$current MAP_VISIBLE=${if(top && visible) 1 else 0}")
        } else {
        if (stack != null) {
            val taskIds=stack.javaClass.getField("taskIds").get(stack) as IntArray
            check(taskIds.size == 1) { "Map shares a stack; refusing to move unrelated applications" }
            val config=stack.javaClass.getField("configuration").get(stack)
            val wc=config.javaClass.getField("windowConfiguration").get(config)
            check(wc.javaClass.getMethod("getActivityType").invoke(wc) == 1) { "Map must be a normal activity" }
            val id = stack.javaClass.getField("stackId").getInt(stack)
            val current = stack.javaClass.getField("displayId").getInt(stack)
            check(current != 1 || args[0]=="launcher-map-claim") { "Map is on passenger display" }
            if (current != destination) apiClass.getMethod("moveStackToDisplay", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(api, id, destination)
        }
        val runner = ProcessCommandRunner(useBridge = false)
        val resolved = runner.run(listOf("/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", pkg), 4)
        val component = resolved.output.lineSequence().map(String::trim).lastOrNull { it.startsWith("$pkg/") && !it.contains(' ') }
        check(component != null) { "Map launcher activity missing" }
        runner.run(listOf("/system/bin/am", "start", "--user", "0", "--display", destination.toString(), "--activity-single-top", "-n", component), 5)
        }
    } catch (e: Exception) { CommandResult(1, "Map preview: ${e.cause?.message ?: e.message}") }
}
