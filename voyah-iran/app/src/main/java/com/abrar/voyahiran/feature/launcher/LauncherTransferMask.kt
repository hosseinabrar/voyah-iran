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
internal class LauncherTransferMask(context: Context) {
    private val wm = context.applicationContext.getSystemService(WindowManager::class.java)
    private val view = View(context.applicationContext).apply { setBackgroundColor(Color.BLACK) }
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private val timeout = Runnable { remove() }
    suspend fun show(): Boolean {
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
            wm.addView(view, WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.OPAQUE)
                .apply { title="VoyahIran central map transfer" })
            attached = true
            handler.postDelayed(timeout, 20_000)
            val ready = withTimeoutOrNull(800) { drawn.await(); true } == true
            if (!ready) remove()
            ready
        } catch (e: Exception) {
            remove()
            com.abrar.voyahiran.core.logging.AppLog.error("LauncherMap", "central mask unavailable", e)
            false
        }
    }
    fun finish(fade: Boolean) {
        if (!attached) return
        if (fade) view.animate().alpha(0f).setDuration(220).withEndAction { remove() }.start()
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
