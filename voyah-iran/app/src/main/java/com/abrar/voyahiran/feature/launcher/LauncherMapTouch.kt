package com.abrar.voyahiran.feature.launcher

import android.view.MotionEvent
import com.abrar.voyahiran.core.bridge.BridgeClient
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*

/** One ordered worker. Coalesce MOVE only; never discard pointer transitions. */
internal class LauncherMapTouch {
    private data class Sample(val pkg:String,val display:Int,val payload:String,val move:Boolean)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val queue=java.util.ArrayDeque<Sample>()
    private var job:Job?=null
    private var gesture:Sample?=null
    fun send(event:MotionEvent,pkg:String,display:Int,width:Int,height:Int):Boolean {
        if(width<=0 || height<=0 || event.pointerCount>5) {cancel();return true}
        if(event.actionMasked==MotionEvent.ACTION_DOWN) cancel()
        else if(gesture==null)return true
        val raw=buildString {
            append(event.downTime);append(';');append(event.eventTime);append(';');append(event.action)
            for(i in 0 until event.pointerCount) {
                append(';');append(event.getPointerId(i));append(',')
                append(java.lang.String.format(java.util.Locale.ROOT,"%.4f",(event.getX(i)/width).coerceIn(0f,1f)));append(',')
                append(java.lang.String.format(java.util.Locale.ROOT,"%.4f",(event.getY(i)/height).coerceIn(0f,1f)))
            }
        }
        val sample=Sample(pkg,display,raw,event.actionMasked==MotionEvent.ACTION_MOVE)
        gesture=sample
        if(queue.size>=32) {cancel();return true}
        if(sample.move && queue.peekLast()?.move==true)queue.removeLast()
        queue.addLast(sample)
        if(event.actionMasked in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))gesture=null
        drain()
        return true
    }
    fun cancel() {
        gesture?.let { sample ->
            val parts=sample.payload.split(';').toMutableList()
            parts[1]=android.os.SystemClock.uptimeMillis().toString();parts[2]=MotionEvent.ACTION_CANCEL.toString()
            queue.clear();queue.addLast(sample.copy(payload=parts.joinToString(";"),move=false))
            gesture=null;drain()
        }
    }
    private fun drain() {
        if(job?.isActive==true)return
        job=scope.launch {
            while(queue.isNotEmpty()) {
                val sample=queue.removeFirst()
                val result=withContext(Dispatchers.IO) {
                    BridgeClient.run(listOf("launcher-map-touch",sample.pkg,sample.display.toString(),sample.payload),1)
                }
                if(result.exitCode!=0 || result.timedOut) {
                    queue.clear();gesture=null
                    AppLog.changed("MapTouch","${result.output}")
                }
            }
        }
    }
}
