package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.view.*

/** Only the OEM media display is allowed to host this transparent output window. */
class ClusterMapActivity : Activity(), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private var output: Surface? = null
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
        // Compose the masked buffer INSIDE the translucent activity, as in the
        // cluster test app. An on-top SurfaceView adds a separate compositor layer
        // on the OEM virtual display; its transparent regions can become black.
        // TextureView stays GPU-backed and consumes the same relay output (no copy
        // to Bitmap, second map instance, or screenshot loop).
        setContentView(TextureView(this).apply {
            isOpaque = false
            surfaceTextureListener = this@ClusterMapActivity
        })
        handler.post(check)
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
        output = Surface(texture)
        session?.let { LauncherClusterMap.surface(it, output, w, h) }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {
        session?.let { LauncherClusterMap.surface(it, output, w, h) }
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        session?.let { LauncherClusterMap.surface(it, null, 0, 0) }
        output?.release()
        output = null
        return true
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
