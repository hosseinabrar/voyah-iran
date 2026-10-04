package com.abrar.voyahiran.feature.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.feature.navigation.NavigationPreferences
import kotlinx.coroutines.*

class VoyahLauncherActivity : ComponentActivity() {
    private lateinit var dashboard: LauncherDashboard
    private lateinit var container: FrameLayout
    private var preview: LauncherMapPreview? = null
    private var mapView: TextureView? = null
    private var activeNavigator = ""
    private var ticker: Job? = null
    private val weather by lazy { LauncherWeather(this,lifecycleScope) { dashboard.weather=it } }
    private val artwork by lazy { LauncherArtwork(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        container=FrameLayout(this).apply { setBackgroundColor(Color.BLACK); layoutDirection=View.LAYOUT_DIRECTION_LTR }
        dashboard=LauncherDashboard(this)
        setContentView(container)
        dashboard.onMap = { preview?.open { finish() } }
        dashboard.onShowMap = { preview?.showInCard() }
        dashboard.onMedia = { button ->
            runCatching {
            val controller=dashboard.media.controller
            val key=when(button){0->88;2->87;else->if(dashboard.media.playing)127 else 126}
            if(controller != null) {
                when(key){88->controller.transportControls.skipToPrevious();87->controller.transportControls.skipToNext();127->controller.transportControls.pause();else->controller.transportControls.play()}
            } else {
                val audio=getSystemService(android.media.AudioManager::class.java)
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,key))
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,key))
            }
            }.onFailure { com.abrar.voyahiran.core.logging.AppLog.error("LauncherMedia", "control failed", it) }
        }
        dashboard.onOpenMusic = {
            runCatching { LauncherSourceLaunch.open(this, dashboard.media) }
                .onFailure { com.abrar.voyahiran.core.logging.AppLog.error("LauncherMedia", "open source failed", it) }
        }
        dashboard.onSeek = { position ->
            runCatching {
                dashboard.media.controller?.takeIf {
                    (it.playbackState?.actions ?: 0L) and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L
                }?.transportControls?.seekTo(position.coerceIn(0, dashboard.media.duration))
            }.onFailure { com.abrar.voyahiran.core.logging.AppLog.error("LauncherMedia", "seek failed", it) }
        }
        container.addOnLayoutChangeListener { _,l,t,r,b,ol,ot,or,ob -> if(r-l != or-ol || b-t != ob-ot) layoutContent() }
        layoutContent()
    }
    @Suppress("DEPRECATION") private fun layoutContent() {
        if(container.width==0)return
        // A patched activity already has the safe bounds. Only inset when the window is still full-display.
        val real=Point();windowManager.defaultDisplay.getRealSize(real)
        val location=IntArray(2);container.getLocationOnScreen(location)
        val left=(145f*real.x/1920-location[0]).toInt().coerceAtLeast(0)
        val top=(50f*real.y/720-location[1]).toInt().coerceAtLeast(0)
        val width=container.width-left;val height=container.height-top
        val lp=FrameLayout.LayoutParams(width,height).apply{leftMargin=left;topMargin=top}
        if(dashboard.parent==null)container.addView(dashboard,lp) else dashboard.layoutParams=lp
        if(preview==null) {
            val map=TextureView(this)
            mapView=map
            map.clipToOutline=true
            map.outlineProvider=object:android.view.ViewOutlineProvider(){override fun getOutline(v:View,o:android.graphics.Outline){o.setRoundRect(0,0,v.width,v.height,16f)}}
            val navigator=NavigationPreferences(this).navigator
            activeNavigator=navigator
            preview=LauncherMapPreview(this,lifecycleScope) { dashboard.mapMessage=it }.also {it.packageName(navigator)}
            map.surfaceTextureListener=preview
            preview?.startMonitoring()
            container.addView(map,0,FrameLayout.LayoutParams((width*871f/1775).toInt(),height).apply{leftMargin=left;topMargin=top})
        } else mapView?.layoutParams=FrameLayout.LayoutParams((width*871f/1775).toInt(),height).apply{leftMargin=left;topMargin=top}
    }
    override fun onResume() {
        super.onResume()
        if(!LauncherPreferences.enabled(this) || !(application as VoyahIranApplication).subscriptions.allowed || (window.decorView.display?.displayId ?:0)!=0) {finish();return}
        if(preview!=null && activeNavigator!=NavigationPreferences(this).navigator) {
            preview?.close();preview=null
            mapView?.let(container::removeView);mapView=null
            dashboard.mapMessage="نقشه در دسترس نیست"
            layoutContent()
        }
        preview?.startMonitoring()
        weather.start()
        ticker?.cancel()
        ticker=lifecycleScope.launch {
            while(isActive) {
                dashboard.media=withContext(Dispatchers.IO){ runCatching {
                    val state=LauncherMedia.read(this@VoyahLauncherActivity)
                    if(state.cover==null) state.copy(cover=artwork.load(state.coverUri)) else state
                }.getOrElse { com.abrar.voyahiran.core.logging.AppLog.info("LauncherMedia", "read failed: ${it.javaClass.simpleName}");LauncherMediaState() } }
                if(!LauncherPreferences.enabled(this@VoyahLauncherActivity) || !(application as VoyahIranApplication).subscriptions.allowed) { finish();break }
                delay(1000)
            }
        }
    }
    // Android 9 may pause the host when the map resumes on its virtual display; it is still visible.
    override fun onStop(){preview?.stopMonitoring();weather.stop();ticker?.cancel();super.onStop()}
    override fun onDestroy(){preview?.close();preview=null;super.onDestroy()}
}
