package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import com.abrar.voyahiran.core.shell.CommandResult

/** Short-lived ownership of a fallback launch, never of an arbitrary player window. */
internal object MusicLaunchGuard {
    private data class Lease(val pkg:String,val display:Int,val previousPkg:String,val previousTask:Int,val expires:Long)
    private val leases=mutableMapOf<String,Lease>()
    internal fun decision(current:String?, player:String, previous:String):String = when(current) {
        player -> "restore"
        previous -> "wait"
        else -> "cancel"
    }
    @Synchronized fun execute(args:List<String>):CommandResult = try {
        val now=android.os.SystemClock.elapsedRealtime()
        leases.entries.removeAll {it.value.expires<now}
        val modern=android.os.Build.VERSION.SDK_INT>=29
        val cls=Class.forName(if(modern)"android.app.IActivityTaskManager" else "android.app.IActivityManager")
        val api=Class.forName(if(modern)"android.app.ActivityTaskManager" else "android.app.ActivityManager").getMethod("getService").invoke(null)
        val stacks=(cls.getMethod("getAllStackInfos").invoke(api) as List<*>).filterNotNull()
        fun top(display:Int)=stacks.firstOrNull {it.javaClass.getField("displayId").getInt(it)==display && it.javaClass.getField("visible").getBoolean(it)}
        fun pkg(stack:Any?)=(stack?.javaClass?.getField("topActivity")?.get(stack) as? ComponentName)?.packageName
        if(args[1]=="begin") {
            val display=args[3].toInt();val stack=top(display)
            val previous=pkg(stack)
            val tasks=stack?.javaClass?.getField("taskIds")?.get(stack) as? IntArray
            if(previous==null || previous==args[2] || tasks==null || tasks.isEmpty()) CommandResult(0,"SKIP: no safe background launch")
            else {
                val token=java.util.UUID.randomUUID().toString()
                leases[token]=Lease(args[2],display,previous,tasks.last(),now+20_000)
                CommandResult(0,"TOKEN=$token")
            }
        } else {
            val token=args[2];val lease=leases[token]
            if(args[1]=="end" || lease==null) {leases.remove(token);CommandResult(0,"DONE")}
            else {
                val current=pkg(top(lease.display))
                val action=decision(current,lease.pkg,lease.previousPkg)
                if(action=="cancel") leases.remove(token)
                if(action=="restore") {
                    val previous=stacks.any {it.javaClass.getField("displayId").getInt(it)==lease.display &&
                        (it.javaClass.getField("taskIds").get(it) as IntArray).contains(lease.previousTask)}
                    if(!previous) {leases.remove(token);CommandResult(0,"DONE: previous task gone")}
                    else {
                        cls.getMethod("moveTaskToFront",Class.forName("android.app.IApplicationThread"),String::class.java,
                            Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,android.os.Bundle::class.java)
                            .invoke(api,null,"com.abrar.voyahiran",lease.previousTask,0,null)
                        CommandResult(0,"RESTORED display=${lease.display} task=${lease.previousTask}")
                    }
                } else CommandResult(0,if(action=="cancel")"DONE: foreground changed to $current" else "WAIT")
            }
        }
    } catch(e:Exception) {CommandResult(1,"Music launch guard failed: ${e.cause ?: e}")}
}
