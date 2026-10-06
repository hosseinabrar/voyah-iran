package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.graphics.PixelFormat
import android.view.*
import android.provider.Settings
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*

/** Temporary branch-only experiment. No launcher Activity or embedded input portal. */
object VirtualScreenTest {
    @Volatile var displayId: Int? = null
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var display: VirtualDisplay? = null
    private var view: SurfaceView? = null
    fun start(context: Context) { scope.launch {
        delay(10_000)
        withContext(Dispatchers.IO) {
            repeat(20) {
                val r = ProcessCommandRunner().run(listOf("su","0","launcher-home","disable"), 5)
                AppLog.info("VirtualTest", "disable launcher exit=${r.exitCode} ${r.output}")
                if (r.exitCode == 0) return@withContext
                delay(500)
            }
        }
        while (!Settings.canDrawOverlays(context)) delay(1000)
        val physical = context.getSystemService(DisplayManager::class.java).getDisplay(0)
            ?: return@launch
        val c = context.createDisplayContext(physical)
        val wm = c.getSystemService(android.view.WindowManager::class.java)
        val size = minOf(c.resources.displayMetrics.heightPixels - 100, 600).coerceAtLeast(200)
        val surface = SurfaceView(c)
        view = surface
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) = Unit
            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, height: Int) {
                if (display == null) {
                    display = c.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                        "VoyahIran.VirtualTest", w, height, 160, h.surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                    displayId = display?.display?.displayId
                    AppLog.info("VirtualTest", "created display=$displayId size=${w}x$height")
                    scope.launch(Dispatchers.IO) {
                        repeat(20) {
                            val id = displayId ?: return@launch
                            val r = ProcessCommandRunner().run(listOf("su","0","virtual-test","launch",id.toString()), 8)
                            AppLog.info("VirtualTest", "launch exit=${r.exitCode} ${r.output}")
                            if (r.exitCode != 75) return@launch
                            delay(500)
                        }
                    }
                } else { display?.surface = h.surface; display?.resize(w,height,160) }
            }
            override fun surfaceDestroyed(h: SurfaceHolder) {
                display?.surface = null
                AppLog.info("VirtualTest", "surface detached display=$displayId")
            }
        })
        wm.addView(surface, WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE).apply { gravity=Gravity.CENTER; title="Voyah virtual display test" })
    } }
}
