package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.view.View
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read-only monitoring; passenger ownership changes only on an explicit button press. */
class LauncherMapPreview(private val context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit, private val view:SurfaceView) : SurfaceHolder.Callback {
    private val embedded=LauncherEmbeddedMapSurface(context,view)
    private var holder:SurfaceHolder?=null
    private var surfaceWidth=0
    private var surfaceHeight=0
    private var active=false
    private var job: Job? = null
    private var monitor: Job? = null
    private var packageName = ""
    private var closed = false
    private var initialAttempted=false
    private var busy=false
    private val moves=Mutex()
    var fullscreen = false
        private set
    fun packageName(value: String) { packageName = value }
    override fun surfaceCreated(h:SurfaceHolder) {
        holder=h
        surfaceWidth=view.width;surfaceHeight=view.height
        if(surfaceWidth>0 && surfaceHeight>0)attachSurface()
    }
    override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,hgt:Int) {
        holder=h;surfaceWidth=w;surfaceHeight=hgt
        attachSurface()
    }
    private fun attachSurface() {
        if(ensureDisplay() && active && !initialAttempted) {
            initialAttempted=true
            move(embedded.displayId!!,false)
        }
    }
    private fun ensureDisplay(): Boolean {
        if(closed || packageName.isBlank()) {error("نقشهٔ پیش‌فرض را در تنظیمات انتخاب کنید");return false}
        val h=holder ?: return false
        return try {
            embedded.attach(h,surfaceWidth,surfaceHeight)
            embedded.active(active)
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","native surface attached display=${embedded.displayId} size=${surfaceWidth}x$surfaceHeight")
            true
        } catch(e:Exception) {
            embedded.detach()
            error("نقشه در دسترس نیست")
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","native surface attachment failed",e)
            false
        }
    }
    private fun move(id: Int, claim: Boolean, done: (() -> Unit)? = null) {
        if(closed || busy)return
        busy=true
        error("در حال نمایش نقشه…")
        job=scope.launch {
            try {
                moves.withLock {
                    if(closed || ((fullscreen || !active) && id!=0))return@withLock
                    val r=withContext(Dispatchers.IO) {
                        ProcessCommandRunner().run(listOf("su","0",if(claim)"launcher-map-claim" else "launcher-map",packageName,id.toString()),15)
                    }
                    if(r.exitCode!=0 || r.timedOut || r.output.contains("Error:")) {
                        error(if(r.output.contains("passenger display")) "نقشه روی نمایشگر شاگرد است" else "نقشه در دسترس نیست")
                        com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap",r.output)
                    } else if(id==0) {fullscreen=true;error("");done?.invoke()}
                    // For the card, wait for read-only ownership verification before uncovering it.
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {error("نقشه در دسترس نیست");com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","move failed",e)}
            finally {busy=false}
        }
    }
    fun showInCard() {
        if(ensureDisplay()) move(embedded.displayId!!,true)
    }
    fun open(done: () -> Unit) { if(packageName.isNotBlank())move(0,false,done) }
    fun startMonitoring() {
        active=true
        view.visibility=View.VISIBLE
        runCatching {embedded.active(true)}.onFailure {error("نقشه در دسترس نیست")}
        if(monitor?.isActive==true)return
        monitor=scope.launch {
            while(isActive && !closed && !fullscreen) {
                val id=embedded.displayId
                if(id!=null && embedded.attached && !busy) {
                    try { moves.withLock {
                        if(busy || closed || fullscreen)return@withLock
                        val r=withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su","0","launcher-map-status",packageName,id.toString()),4)
                        }
                        if(!active || !embedded.attached || embedded.displayId!=id)return@withLock
                        if(r.exitCode==0 && !r.timedOut) {
                            error(if(isVisibleHere(r.output,id)) "" else if(r.output.contains("MAP_DISPLAY=1 ")) "نقشه روی نمایشگر شاگرد است" else "نقشه در دسترس نیست")
                        } else if(r.exitCode!=75) error("نقشه در دسترس نیست")
                    } } catch(e:CancellationException) {throw e}
                    catch(e:Exception) {error("نقشه در دسترس نیست")}
                }
                delay(2000)
            }
        }
    }
    fun stopMonitoring() {
        active=false;monitor?.cancel();monitor=null
        runCatching {embedded.active(false)}
        view.visibility=View.INVISIBLE
    }
    override fun surfaceDestroyed(h:SurfaceHolder) {
        holder=null;embedded.detach()
        if(!fullscreen)error("نقشه در دسترس نیست")
    }
    fun close() {
        closed=true;stopMonitoring();job?.cancel()
        view.holder.removeCallback(this)
        embedded.close();holder=null
    }
    companion object {
        internal fun isVisibleHere(output: String, displayId: Int): Boolean =
            Regex("(?:^|\\s)MAP_DISPLAY=$displayId MAP_VISIBLE=1(?:$|\\s)").containsMatchIn(output)
    }
}
