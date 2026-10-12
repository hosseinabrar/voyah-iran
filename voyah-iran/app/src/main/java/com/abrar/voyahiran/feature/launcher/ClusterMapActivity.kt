package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.view.*

/** Only the OEM media display is allowed to host this transparent output window. */
class ClusterMapActivity : Activity(), SurfaceHolder.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private var session: String? = null
    private val check = object : Runnable {
        override fun run() {
            if (!LauncherClusterMap.valid(this@ClusterMapActivity,session)) { finishAndRemoveTask(); return }
            handler.postDelayed(this,100)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        session=intent.getStringExtra("session")
        val display=windowManager.defaultDisplay
        val name=display.name
        if (!LauncherClusterMap.valid(this,session) || display.displayId<=1 ||
            !name.contains("Cluster",ignoreCase=true) || !name.contains("Media",ignoreCase=true)) { finish();return }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(SurfaceView(this).apply {
            setZOrderOnTop(true); holder.setFormat(PixelFormat.TRANSLUCENT);holder.addCallback(this@ClusterMapActivity)
        })
        handler.post(check)
    }
    override fun surfaceCreated(holder: SurfaceHolder) = Unit
    override fun surfaceChanged(holder: SurfaceHolder,format: Int,w: Int,h: Int) { session?.let {LauncherClusterMap.surface(it,holder.surface,w,h)} }
    override fun surfaceDestroyed(holder: SurfaceHolder) { session?.let {LauncherClusterMap.surface(it,null,0,0)} }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
