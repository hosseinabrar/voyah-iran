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
        fun move(s:Any,to:Int) {
            AppLog.info("VirtualTest","moveStackToDisplay stack=${id(s)} from=${screen(s)} to=$to")
            apiType.getMethod("moveStackToDisplay",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).invoke(api,id(s),to)
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
                move(virtual,1)
                move(passenger,display)
                val after=stacks()
                check(after.any { id(it)==id(virtual) && screen(it)==1 } &&
                    after.any { id(it)==id(passenger) && screen(it)==display }) { "Swap incomplete" }
            }
        } finally { dump("after $operation") }
        CommandResult(0,"DONE")
    } catch(e:Exception) {
        val cause=e.cause ?: e
        AppLog.error("VirtualTest","transfer failed; preserving actual state for diagnosis",cause)
        CommandResult(1,"${cause.javaClass.simpleName}: ${cause.message}")
    }
}
