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

/** Retains a display while fullscreen map owns its task; no synthetic touches go to the map. */
class LauncherMapPreview(private val context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit) : TextureView.SurfaceTextureListener {
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var texture: SurfaceTexture? = null
    private var job: Job? = null
    private var packageName = ""
    private var closed = false
    private val moves=Mutex()
    var fullscreen = false
        private set
    fun packageName(value: String) { packageName = value }
    override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
        if (closed || packageName.isBlank()) { error("نقشهٔ پیش‌فرض را در تنظیمات انتخاب کنید"); return }
        texture = t
        try {
            surface?.release()
            surface = Surface(t)
            if(display == null) {
                // PUBLIC + OWN_CONTENT_ONLY: this is a real app display, never a screen mirror.
                display = context.getSystemService(DisplayManager::class.java).createVirtualDisplay("VoyahIran.MapPreview", w, h, 160, surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
            } else { display?.resize(w,h,160); display?.surface=surface }
            move(display!!.display.displayId)
        } catch (e: Exception) { error("پیش‌نمایش نقشه در دسترس نیست؛ برای بازکردن لمس کنید"); com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap", "display creation", e) }
    }
    private fun move(id: Int, done: (() -> Unit)? = null) {
        job = scope.launch {
            moves.withLock {
            if(closed || (fullscreen && id!=0))return@withLock
            val r = withContext(Dispatchers.IO) { ProcessCommandRunner().run(listOf("su", "0", "launcher-map", packageName, id.toString()), 10) }
            if (r.exitCode != 0 || r.timedOut || r.output.contains("Error:")) {
                error("پیش‌نمایش نقشه آماده نشد؛ برای بازکردن لمس کنید")
                com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap", r.output)
            } else { error(""); done?.invoke() }
            }
        }
    }
    fun open(done: () -> Unit) {
        if (packageName.isBlank()) return
        fullscreen = true
        move(0) { done() }
    }
    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) { display?.resize(w, h, 160) }
    override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean { display?.surface = null; texture = null; return true }
    fun close() {
        closed = true; job?.cancel()
        display?.release(); display = null
        surface?.release(); surface = null; texture = null
    }
}
