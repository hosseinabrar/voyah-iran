package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Uses an application overlay so stopping HOME does not uncover an unfinished resize. */
internal class LauncherTransferMask(private val context: Context, private val packageName: String, private val displayId: Int = 0) {
    private val display = requireNotNull(context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId))
    // Application context loses the passenger display association.
    private val displayContext = context.applicationContext.createDisplayContext(display)
    private val wm = displayContext.getSystemService(WindowManager::class.java)
    private val view = View(displayContext)
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private val timeout = Runnable { remove() }
    suspend fun show(): Boolean {
        view.setBackgroundColor(LauncherWindowTheme.background(LauncherTheme.isLight()))
        val drawn = CompletableDeferred<Unit>()
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                view.post {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this)
                    drawn.complete(Unit)
                }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
        return try {
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val size = android.graphics.Point().also { display.getRealSize(it) }
            val resolver = context.contentResolver
            val request = android.provider.Settings.Global.getString(resolver,"voyah_iran_display_policy")
            val ack = android.provider.Settings.Global.getString(resolver,"voyah_iran_display_policy_ack")
            val eligible = runCatching {
                val app=context.packageManager.getApplicationInfo(packageName,0)
                packageName==context.packageName || (app.flags and 129 == 0 && app.uid >= 10000)
            }.getOrDefault(false)
            val bounds = LauncherTransferBounds.resolve(size.x,size.y,request,ack,eligible)
            wm.addView(view, WindowManager.LayoutParams(bounds.width(),bounds.height(),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.OPAQUE)
                .apply { title="VoyahIran map transfer display=$displayId"; gravity=android.view.Gravity.TOP or android.view.Gravity.LEFT; x=bounds.left; y=bounds.top })
            attached = true
            handler.postDelayed(timeout, 20_000)
            val ready = withTimeoutOrNull(800) { drawn.await(); true } == true
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","mask drawn=$ready display=$displayId bounds=$bounds")
            if (!ready) remove()
            ready
        } catch (e: Exception) {
            remove()
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap", "mask unavailable display=$displayId", e)
            false
        }
    }
    fun finish(fade: Boolean) {
        if (!attached) return
        com.abrar.voyahiran.core.logging.AppLog.info("LauncherMap","mask finish display=$displayId fade=$fade")
        if (fade) view.animate().alpha(0f).setDuration(350).withEndAction { remove() }.start()
        else remove()
    }
    private fun remove() {
        handler.removeCallbacks(timeout)
        if (!attached) return
        attached = false
        view.animate().cancel()
        runCatching { wm.removeViewImmediate(view) }
    }
}
