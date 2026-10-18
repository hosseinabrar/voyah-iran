package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

/** Moves only the selected map's normal activity stack; never moves a vehicle/system stack. */
internal object LauncherMapBridge {
    @Synchronized fun execute(args: List<String>): CommandResult = try {
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
        if(args[0]=="launcher-map-theme") {
            check(BridgeSubscriptionSession.vipActive) {"VIP subscription inactive"}
            check(destination>1)
            check(args.size==4 && args[3] in listOf("light","dark"))
            val light=args[3]=="light"
            // A sparse display override updates uiMode only: no resizing, task move, or process restart.
            val config=android.content.res.Configuration().apply {
                uiMode=if(light) android.content.res.Configuration.UI_MODE_NIGHT_NO else android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val update=try {
                apiClass.getMethod("updateDisplayOverrideConfiguration",android.content.res.Configuration::class.java,Int::class.javaPrimitiveType)
            } catch(_:NoSuchMethodException) {
                // H97X Android 11 does not expose this binder method. Never fall back
                // to global updateConfiguration: that would change both physical screens.
                return CommandResult(69,"MAP_THEME_UNSUPPORTED: per-display override API absent")
            }
            update.invoke(api,config,destination)
            return CommandResult(0,"MAP_THEME display=$destination light=$light")
        }
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
        if (args[0] == "launcher-map-clean") {
            check(BridgeSubscriptionSession.vipActive) { "VIP subscription inactive" }
            check(pkg != "com.abrar.voyahiran" && destination > 1)
            // Never kill a map owned by a physical display, even if it moved during the request.
            if (candidates.all { it.javaClass.getField("displayId").getInt(it) == destination }) {
                val inventory = ProcessCommandRunner(useBridge=false).run(
                    listOf("/system/bin/pm", "list", "packages", "-3", "--user", "0"), 5)
                check(inventory.exitCode == 0 && !inventory.timedOut &&
                    inventory.output.lineSequence().any { it.trim() == "package:$pkg" }) {
                    "Clean restart requires a user-installed map"
                }
                val stopped = ProcessCommandRunner(useBridge=false).run(
                    listOf("/system/bin/am", "force-stop", "--user", "0", pkg), 5)
                check(stopped.exitCode == 0 && !stopped.timedOut && !stopped.output.contains("Error:")) {
                    "Map clean stop failed: ${stopped.output}"
                }
            }
            return execute(listOf("launcher-map", pkg, destination.toString()))
        }
        if (release && (stack == null || stack.javaClass.getField("displayId").getInt(stack) != sourceDisplay))
            return CommandResult(0, "Map already outside preview")
        if(args[0]=="launcher-map-route") {
            check(BridgeSubscriptionSession.vipActive) { "VIP subscription inactive" }
            check(candidates.any {
                it.javaClass.getField("displayId").getInt(it)==destination &&
                    (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName==pkg &&
                    it.javaClass.getField("visible").getBoolean(it)
            }) { "Map no longer visible on requested display" }
            // The host app cannot launch a foreign activity on this virtual display.
            // Use the same privileged transport as embedding, without moving its stack.
            return ProcessCommandRunner(useBridge=false).run(listOf(
                "/system/bin/am", "start", "--user", "0", "--display", destination.toString(),
                "-a", "android.intent.action.VIEW", "-d", "geo:${args[3].toDouble()},${args[4].toDouble()}",
                "-p", pkg, "-f", "0x10000000"), 8)
        }
        if(args[0]=="launcher-map-status") {
            // Status queries prefer an actually visible map over a background copy.
            val visibleStack=candidates.firstOrNull {
                (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName==pkg &&
                    runCatching { it.javaClass.getField("visible").getBoolean(it) }.getOrDefault(false)
            } ?: stack
            val current=visibleStack?.let { it.javaClass.getField("displayId").getInt(it) } ?: -1
            val top=visibleStack?.let { (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName==pkg } == true
            val visible=visibleStack?.let { runCatching { it.javaClass.getField("visible").getBoolean(it) }.getOrDefault(top) } == true
            val fitted = if (current in 0..1 && visibleStack != null) runCatching {
                val bounds = visibleStack.javaClass.getField("bounds").get(visibleStack) as android.graphics.Rect
                val global = Class.forName("android.hardware.display.DisplayManagerGlobal")
                val info = global.getMethod("getDisplayInfo", Int::class.javaPrimitiveType)
                    .invoke(global.getMethod("getInstance").invoke(null), current)
                bounds.isEmpty || (bounds.width() >= info.javaClass.getField("logicalWidth").getInt(info) * .9 &&
                    bounds.height() >= info.javaClass.getField("logicalHeight").getInt(info) * .9)
            }.getOrDefault(false) else false
            CommandResult(0,"MAP_DISPLAY=$current MAP_VISIBLE=${if(top && visible) 1 else 0} MAP_FITTED=${if(fitted) 1 else 0}")
        } else {
        if (stack != null) {
            val taskIds=stack.javaClass.getField("taskIds").get(stack) as IntArray
            check(taskIds.size == 1) { "Map shares a stack; refusing to move unrelated applications" }
            val config=stack.javaClass.getField("configuration").get(stack)
            val wc=config.javaClass.getField("windowConfiguration").get(config)
            check(wc.javaClass.getMethod("getActivityType").invoke(wc) == 1) { "Map must be a normal activity" }
            val current = stack.javaClass.getField("displayId").getInt(stack)
            check(current != 1 || args[0]=="launcher-map-claim") { "Map is on passenger display" }
            if(release) {
                val focused=apiClass.getMethod("getFocusedStackInfo").invoke(api)
                val focusedPackage=focused?.let { (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.packageName }
                if(focusedPackage!=pkg) return CommandResult(0,"Map retained in preview; foreground=$focusedPackage")
            }
            val stackId=stack.javaClass.getField("stackId").getInt(stack)
            fun live()=(apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>).filterNotNull().firstOrNull {
                it.javaClass.getField("stackId").getInt(it)==stackId && containsMap(it)
            }
            fun fit() {
                apiClass.getMethod("setTaskWindowingMode",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType)
                    .invoke(api,taskIds.single(),1,false)
                val resized=apiClass.getMethod("resizeTask",Int::class.javaPrimitiveType,android.graphics.Rect::class.java,Int::class.javaPrimitiveType)
                    .invoke(api,taskIds.single(),null,0)
                apiClass.getMethod("setFocusedStack",Int::class.javaPrimitiveType).invoke(api,stackId)
                com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","fit stack=$stackId resizeResult=$resized bounds=${live()?.let { it.javaClass.getField("bounds").get(it) }}")
            }
            fun move(to:Int) {
                val before=checkNotNull(live()) { "Map stack disappeared" }
                if(before.javaClass.getField("displayId").getInt(before)!=to) {
                    try {
                        apiClass.getMethod("moveStackToDisplay",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).invoke(api,stackId,to)
                    } catch(e:java.lang.reflect.InvocationTargetException) {
                        val cause=e.targetException
                        val landed=live()?.let { it.javaClass.getField("displayId").getInt(it)==to }==true
                        if(cause !is NullPointerException || cause.message?.contains("moveFocusableActivityToTop")!=true || !landed) throw e
                        com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","OEM focus error after confirmed move; completing layout",cause)
                    }
                }
                check(live()?.let {it.javaClass.getField("displayId").getInt(it)==to}==true) {"Map stack did not reach display $to"}
                fit()
            }
            // The tested swap leaves another activity in the virtual source. A map handoff
            // has no second app, so retain a dedicated empty activity underneath it instead.
            if(current>1) ensureAnchor(current,apiClass,api)
            if(destination>1) ensureAnchor(destination,apiClass,api)
            try { move(destination) } catch(e:Exception) {
                runCatching { move(current) }.onFailure {
                    com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","rollback failed stack=$stackId",it)
                }
                throw e
            }
            val after=apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>
            check(after.filterNotNull().any {
                it.javaClass.getField("displayId").getInt(it)==destination &&
                    (it.javaClass.getField("taskIds").get(it) as IntArray).contains(taskIds.single()) && containsMap(it)
            }) { "Map task did not reach display $destination" }
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", "stack-transfer stack=$stackId task=${taskIds.single()} display=$current->$destination release=$release")
            return CommandResult(0,"Map stack moved to display $destination")
        }
        if(release) return CommandResult(0, "Map returned to central display")
        if(destination>1) ensureAnchor(destination,apiClass,api)
        val runner = ProcessCommandRunner(useBridge = false)
        val resolved = runner.run(listOf("/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", pkg), 4)
        val component = resolved.output.lineSequence().map(String::trim).lastOrNull { it.startsWith("$pkg/") && !it.contains(' ') }
        check(component != null) { "Map launcher activity missing" }
        runner.run(launchArguments(component,destination), 5)
        }
    } catch (e: Exception) { CommandResult(1, "Map preview: ${e.cause?.message ?: e.message}") }

    private fun ensureAnchor(display:Int,apiClass:Class<*>,api:Any) {
        val global=Class.forName("android.hardware.display.DisplayManagerGlobal")
        val info=global.getMethod("getDisplayInfo",Int::class.javaPrimitiveType).invoke(global.getMethod("getInstance").invoke(null),display)
        check(info!=null && info.javaClass.getField("name").get(info)=="VoyahIran.MapPreview" &&
            info.javaClass.getField("ownerPackageName").get(info)=="com.abrar.voyahiran") {"Not an owned preview display"}
        val anchor="com.abrar.voyahiran.feature.launcher.LauncherMapAnchorActivity"
        fun present()=(apiClass.getMethod("getAllStackInfos").invoke(api) as List<*>).filterNotNull().any {
            it.javaClass.getField("displayId").getInt(it)==display &&
                (it.javaClass.getField("topActivity").get(it) as? ComponentName)?.className==anchor
        }
        if(present())return
        val r=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am","start","--user","0","--display",display.toString(),
            "-n","com.abrar.voyahiran/$anchor"),4)
        check(r.exitCode==0 && !r.timedOut && !r.output.contains("Error:")) {"Preview anchor launch failed: ${r.output}"}
        repeat(20) { if(present())return; android.os.SystemClock.sleep(50) }
        error("Preview anchor did not become ready")
    }

    internal fun launchArguments(component:String,destination:Int):List<String> =
        listOf("/system/bin/am","start","--user","0","--display",destination.toString()) +
            if(destination==0) listOf("--windowingMode","1","-a","android.intent.action.MAIN",
                "-c","android.intent.category.LAUNCHER","-f","0x10200000","-n",component)
            else listOf("--activity-single-top","-n",component)
}
