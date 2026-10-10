package com.abrar.voyahiran.feature.launcher

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.View

/** Keeps a focusable source activity below the map while its stack leaves the preview. */
class LauncherMapAnchorActivity : Activity(), DisplayManager.DisplayListener {
    private var previewId=-1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION") val current = windowManager.defaultDisplay
        if(current.displayId<=1 || current.name!="VoyahIran.MapPreview") { finish(); return }
        previewId=current.displayId
        getSystemService(DisplayManager::class.java).registerDisplayListener(this,Handler(Looper.getMainLooper()))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(View(this).apply {setBackgroundColor(Color.BLACK)})
    }
    override fun onDisplayAdded(displayId:Int) = Unit
    override fun onDisplayChanged(displayId:Int) = Unit
    override fun onDisplayRemoved(displayId:Int) { if(displayId==previewId)finishAndRemoveTask() }
    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(this)
        super.onDestroy()
    }
}
