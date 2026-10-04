package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import android.view.TextureView
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read-only monitoring; passenger ownership changes only on an explicit button press. */
class LauncherMapPreview(private val context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var texture: SurfaceTexture? = null
    private var surfaceWidth=0
    private var surfaceHeight=0
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
    override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
        if(closed)return
        texture=t;surfaceWidth=w;surfaceHeight=h
        if(ensureDisplay() && !initialAttempted) {
            initialAttempted=true
            move(display!!.display.displayId,false)
        }
    }
    private fun ensureDisplay(): Boolean {
        if(closed || packageName.isBlank()) {error("نقشهٔ پیش‌فرض را در تنظیمات انتخاب کنید");return false}
        val t=texture ?: run {error("نقشه در دسترس نیست");return false}
        return try {
            if(surface==null)surface=Surface(t)
            if(display==null) display=context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "VoyahIran.MapPreview",surfaceWidth,surfaceHeight,160,surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
            else {display?.resize(surfaceWidth,surfaceHeight,160);display?.surface=surface}
            true
        } catch(e:Exception) {
            error("نقشه در دسترس نیست")
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap","display creation",e)
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
                    if(closed || (fullscreen && id!=0))return@withLock
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
        if(ensureDisplay()) move(display!!.display.displayId,true)
    }
    fun open(done: () -> Unit) { if(packageName.isNotBlank())move(0,false,done) }
    fun startMonitoring() {
        if(monitor?.isActive==true)return
        monitor=scope.launch {
            while(isActive && !closed && !fullscreen) {
                val id=display?.display?.displayId
                if(id!=null && !busy) {
                    try { moves.withLock {
                        if(busy || closed || fullscreen)return@withLock
                        val r=withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su","0","launcher-map-status",packageName,id.toString()),4)
                        }
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
    fun stopMonitoring() {monitor?.cancel();monitor=null}
    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture,w: Int,h: Int) {surfaceWidth=w;surfaceHeight=h;display?.resize(w,h,160)}
    override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
        display?.surface=null;surface?.release();surface=null;texture=null;return true
    }
    fun close() {
        closed=true;stopMonitoring();job?.cancel()
        display?.release();display=null;surface?.release();surface=null;texture=null
    }
    companion object {
        internal fun isVisibleHere(output: String, displayId: Int): Boolean =
            Regex("(?:^|\\s)MAP_DISPLAY=$displayId MAP_VISIBLE=1(?:$|\\s)").containsMatchIn(output)
    }
}
