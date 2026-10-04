package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

/** Moves only the selected map's normal activity stack; never moves a vehicle/system stack. */
internal object LauncherMapBridge {
    fun execute(args: List<String>): CommandResult = try {
        check(BridgeSubscriptionSession.active) { "Subscription inactive" }
        val pkg = args[1]
        val release = args[0] == "launcher-map-release"
        val sourceDisplay = args[2].toInt()
        val destination = if (release) 0 else sourceDisplay
        if (destination != 0 || release) {
            val global = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            val info = global.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(instance, sourceDisplay)
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
        if (release && (stack == null || stack.javaClass.getField("displayId").getInt(stack) != sourceDisplay))
            return CommandResult(0, "Map already outside preview")
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
            val current = stack.javaClass.getField("displayId").getInt(stack)
            check(current != 1 || args[0]=="launcher-map-claim") { "Map is on passenger display" }
            if(current==destination && destination!=0) return CommandResult(0,"Map already in preview")
            if(release) {
                val focused=apiClass.getMethod("getFocusedStackInfo").invoke(api)
                val focusedPackage=focused?.let { (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName }
                if(focusedPackage!=pkg) return CommandResult(0,"Map retained in preview; foreground=$focusedPackage")
            }
            val host=if(destination!=0) stacks.filterNotNull().firstOrNull {
                it.javaClass.getField("displayId").getInt(it)==0 &&
                    (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.className==
                    "com.abrar.voyahiran.feature.launcher.VoyahLauncherActivity"
            } else null
            // Use the normal task-launch path, which completes configuration/resume and
            // surface updates. OEM moveStackToDisplay can throw halfway through reparenting.
            val options=android.app.ActivityOptions.makeBasic().setLaunchDisplayId(destination)
            android.app.ActivityOptions::class.java.getMethod("setLaunchWindowingMode",Int::class.javaPrimitiveType)
                .invoke(options,1)
            val result=apiClass.getMethod("startActivityFromRecents",Int::class.javaPrimitiveType,android.os.Bundle::class.java)
                .invoke(api,taskIds.single(),options.toBundle()) as Int
            check(result>=0) { "Map task launch failed: $result" }
            // The recents entry point raises HOME. Keep our host above HOME on the central
            // screen when embedding; this does not relaunch or move the host activity.
            if(host!=null) {
                val hostTasks=host.javaClass.getField("taskIds").get(host) as IntArray
                apiClass.getMethod("moveTaskToFront",Class.forName("android.app.IApplicationThread"),String::class.java,
                    Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,android.os.Bundle::class.java)
                    .invoke(api,null,"com.android.shell",hostTasks.last(),0,null)
            }
            apiClass.getMethod("resizeTask",Int::class.javaPrimitiveType,android.graphics.Rect::class.java,Int::class.javaPrimitiveType)
                .invoke(api,taskIds.single(),null,0)
            val after=apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>
            check(after.filterNotNull().any {
                it.javaClass.getField("displayId").getInt(it)==destination &&
                    (it.javaClass.getField("taskIds").get(it) as IntArray).contains(taskIds.single()) && containsMap(it)
            }) { "Map task did not reach display $destination" }
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", "task-launch task=${taskIds.single()} display=$current->$destination result=$result release=$release")
            return CommandResult(0,"Map task launched on display $destination")
        }
        if(release) return CommandResult(0, "Map returned to central display")
        val runner = ProcessCommandRunner(useBridge = false)
        val resolved = runner.run(listOf("/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", pkg), 4)
        val component = resolved.output.lineSequence().map(String::trim).lastOrNull { it.startsWith("$pkg/") && !it.contains(' ') }
        check(component != null) { "Map launcher activity missing" }
        runner.run(launchArguments(component,destination), 5)
        }
    } catch (e: Exception) { CommandResult(1, "Map preview: ${e.cause?.message ?: e.message}") }

    internal fun launchArguments(component:String,destination:Int):List<String> =
        listOf("/system/bin/am","start","--user","0","--display",destination.toString()) +
            if(destination==0) listOf("--windowingMode","1","-a","android.intent.action.MAIN",
                "-c","android.intent.category.LAUNCHER","-f","0x10200000","-n",component)
            else listOf("--activity-single-top","-n",component)
}
