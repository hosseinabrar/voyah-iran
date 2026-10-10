package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object VirtualScreenTestBridge {
    @Synchronized fun execute(operation: String, display: Int): CommandResult = try {
        val global=Class.forName("android.hardware.display.DisplayManagerGlobal")
        val info=global.getMethod("getDisplayInfo",Int::class.javaPrimitiveType)
            .invoke(global.getMethod("getInstance").invoke(null),display)
        check(display>1 && info!=null && info.javaClass.getField("name").get(info)=="VoyahIran.VirtualTest" &&
            info.javaClass.getField("ownerPackageName").get(info)=="com.abrar.voyahiran") { "Invalid test display" }
        val modern=android.os.Build.VERSION.SDK_INT>=29
        val apiType=Class.forName(if(modern)"android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val api=Class.forName(if(modern)"android.app.ActivityTaskManager" else "android.app.ActivityManager").getMethod("getService").invoke(null)
        fun stacks()=(apiType.getMethod("getAllStackInfos").invoke(api) as List<*>).filterNotNull()
        fun id(s:Any)=s.javaClass.getField("stackId").getInt(s)
        fun screen(s:Any)=s.javaClass.getField("displayId").getInt(s)
        fun top(s:Any)=s.javaClass.getField("topActivity").get(s) as? ComponentName
        fun dump(label:String) { AppLog.info("VirtualTest", "$label "+stacks().joinToString { s ->
            "stack=${id(s)} display=${screen(s)} top=${top(s)} bounds=${s.javaClass.getField("bounds").get(s)}" }) }
        fun live(s:Any)=stacks().firstOrNull { id(it)==id(s) && top(it)?.packageName==top(s)?.packageName }
        fun fit(s:Any,to:Int) {
            val current=checkNotNull(live(s)) { "Transferred stack disappeared" }
            check(screen(current)==to) { "Stack did not reach display $to" }
            val tasks=current.javaClass.getField("taskIds").get(current) as IntArray
            check(tasks.size==1) { "Refusing to resize a shared stack" }
            apiType.getMethod("setTaskWindowingMode",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType)
                .invoke(api,tasks.single(),1,false)
            val resized=apiType.getMethod("resizeTask",Int::class.javaPrimitiveType,android.graphics.Rect::class.java,Int::class.javaPrimitiveType)
                .invoke(api,tasks.single(),null,0)
            apiType.getMethod("setFocusedStack",Int::class.javaPrimitiveType).invoke(api,id(current))
            AppLog.info("VirtualTest","fit stack=${id(s)} display=$to resizeResult=$resized bounds=${live(s)?.let { it.javaClass.getField("bounds").get(it) }}")
        }
        fun move(s:Any,to:Int) {
            val current=checkNotNull(live(s)) { "Transfer target disappeared" }
            if(screen(current)!=to) {
                AppLog.info("VirtualTest","moveStackToDisplay stack=${id(s)} from=${screen(current)} to=$to")
                try {
                    apiType.getMethod("moveStackToDisplay",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).invoke(api,id(s),to)
                } catch(e:java.lang.reflect.InvocationTargetException) {
                    val cause=e.targetException
                    // This OEM throws after reparenting when the source has no activity to focus.
                    // Only recover that specific failure, and only after confirming the move.
                    if(cause !is NullPointerException || cause.message?.contains("moveFocusableActivityToTop")!=true ||
                        live(s)?.let(::screen)!=to) throw e
                    AppLog.error("VirtualTest","OEM focus failure after confirmed reparent; completing layout",cause)
                }
            }
            fit(s,to)
        }
        dump("before $operation")
        try {
            if(operation=="launch") {
                val pkg="org.rajman.neshan.traffic.tehran.navigator"
                val existing=stacks().firstOrNull { top(it)?.packageName==pkg }
                if(existing!=null) { if(screen(existing)!=display) move(existing,display) }
                else {
                    val runner=ProcessCommandRunner(useBridge=false)
                    val resolved=runner.run(listOf("/system/bin/cmd","package","resolve-activity","--brief","--user","0","-a","android.intent.action.MAIN","-c","android.intent.category.LAUNCHER",pkg),4)
                    val component=resolved.output.lineSequence().map(String::trim).lastOrNull { it.startsWith("$pkg/") && !it.contains(' ') } ?: error("Neshan not installed")
                    val result=runner.run(listOf("/system/bin/am","start","--user","0","--display",display.toString(),"-n",component),5)
                    check(result.exitCode==0 && !result.timedOut && !result.output.contains("Error:")) { result.output }
                }
            } else {
                val all=stacks()
                fun target(d:Int)=all.firstOrNull { s ->
                    val config=s.javaClass.getField("configuration").get(s)
                    val wc=config.javaClass.getField("windowConfiguration").get(config)
                    screen(s)==d && s.javaClass.getField("visible").getBoolean(s) &&
                        wc.javaClass.getMethod("getActivityType").invoke(wc)==1 &&
                        top(s)!=null && top(s)?.packageName !in listOf("com.qinggan.app.launcher","com.abrar.voyahiran")
                }
                val virtual=target(display) ?: error("No app in test square")
                val passenger=target(1) ?: error("Open an app on passenger display first")
                for(s in listOf(virtual,passenger)) {
                    check((s.javaClass.getField("taskIds").get(s) as IntArray).size==1) { "Refusing to move a shared stack" }
                }
                try {
                    // Keep the virtual display occupied: unlike the passenger display it has no HOME.
                    move(passenger,display)
                    move(virtual,1)
                    val after=stacks()
                    check(after.any { id(it)==id(virtual) && screen(it)==1 } &&
                        after.any { id(it)==id(passenger) && screen(it)==display }) { "Swap incomplete" }
                } catch(e:Exception) {
                    // Restore the virtual occupant first, before removing the incoming passenger app.
                    for(s in listOf(virtual,passenger)) runCatching { move(s,screen(s)) }
                        .onFailure { AppLog.error("VirtualTest","rollback failed stack=${id(s)}",it) }
                    throw e
                }
            }
        } finally { dump("after $operation") }
        CommandResult(0,"DONE")
    } catch(e:Exception) {
        val cause=e.cause ?: e
        AppLog.error("VirtualTest","transfer failed; see actual state and rollback results",cause)
        CommandResult(1,"${cause.javaClass.simpleName}: ${cause.message}")
    }
}
