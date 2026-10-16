package com.abrar.voyahiran.feature.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.feature.navigation.NavigationPreferences
import kotlinx.coroutines.*

class VoyahLauncherActivity : ComponentActivity() {
    companion object { @Volatile internal var mapPageVisible=false; private set }
    private lateinit var fullscreenButton:MapFullscreenButton
    private lateinit var dashboard: LauncherDashboard
    private lateinit var container: FrameLayout
    private lateinit var pager: LauncherPager
    private lateinit var firstPage: FrameLayout
    private lateinit var clusterButton: ClusterMapButton
    private lateinit var vehiclePage: LauncherVehicleDashboard
    private var contentReady=false
    private var contentFrameReady=false
    private lateinit var startupCover: View
    private var vehicleTicker: Job? = null
    private var visible=false
    private val vehicleCache=LauncherVehicleCache()
    private val vehicleOrder=VehicleSnapshotOrder()
    private fun acceptVehicleSnapshot(s:org.json.JSONObject){
        if(!vehicleOrder.accept(s.optLong("session",0),s.optLong("revision",-1)))return
        val state=LauncherVehicleState.parse(s)
        lastVehicleRead=android.os.SystemClock.elapsedRealtime()
        vehicleCache.accept(state);vehiclePage.state=vehicleCache.display;compass.speed=state.speed
    }
    private var lastVehicleRead=0L
    private val compassDelegate=lazy { LauncherCompass(this) {vehiclePage.compass=it} }
    private val compass by compassDelegate
    private var drivePopup: android.widget.PopupWindow? = null
    private var preview: LauncherMapPreview? = null
    private var mapView: SurfaceView? = null
    private var activeNavigator = ""
    private var ticker: Job? = null
    private var defaultPlayJob: Job? = null
    private var coverJob: Job? = null
    private var coverKey = emptyList<String>()
    private var mediaAt=0L
    private var mediaSnapshot=LauncherMediaState()
    private val mediaObserver by lazy { LauncherMediaObserver(this,lifecycleScope) { state ->
        mediaSnapshot=state;mediaAt=android.os.SystemClock.elapsedRealtime()
        val key=listOf(state.sourcePackage,state.oemSource,state.source,state.title,state.artist,state.coverUri)
        val sameCover=key==coverKey
        if(!sameCover){coverJob?.cancel();coverKey=key}
        if(state.cover!=null)coverJob?.cancel()
        // Playback/position callbacks must never briefly replace the decoded cover with the placeholder.
        val retained=if(sameCover)dashboard.media.cover else null
        dashboard.media=state.copy(cover=state.cover ?: retained,coverPending=state.cover==null && retained==null && state.coverUri.isNotBlank())
        if(dashboard.media.cover==null && state.coverUri.isNotBlank() && coverJob?.isActive!=true) coverJob=lifecycleScope.launch {
            val cover=artwork.loadWithRetry(state.coverUri,key.toString())
            if(coverKey==key)dashboard.media=dashboard.media.copy(cover=cover,coverPending=false)
        }
    } }
    private val weather by lazy { LauncherSharedWeather(this) { dashboard.weather=it } }
    private val artwork by lazy { LauncherArtwork(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherWindowTheme.apply(window,LauncherTheme.isLight())
        val color=if(LauncherTheme.isLight()) Color.WHITE else Color.BLACK
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
        container=FrameLayout(this).apply { setBackgroundColor(color); layoutDirection=View.LAYOUT_DIRECTION_LTR }
        startupCover=View(this).apply { setBackgroundColor(color); isClickable=true }
        container.addView(startupCover,FrameLayout.LayoutParams(-1,-1))
        setContentView(container)
        // Post from an actual draw, not two arbitrary queue posts: the cheap cover gets a frame first.
        container.afterFrame {
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherWake", "Startup cover frame drawn")
            if(!isFinishing && !isDestroyed) buildContent()
        }
    }
    private fun buildContent() {
        dashboard=LauncherDashboard(this)
        vehiclePage=LauncherVehicleDashboard(this)
        pager=LauncherPager(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // HOME stays open: Back only returns from page two to page one.
                if (pager.page != 0) pager.show(0)
            }
        })
        firstPage=FrameLayout(this)
        firstPage.addView(dashboard,FrameLayout.LayoutParams(-1,-1))
        clusterButton=ClusterMapButton(this).apply {
            visibility=View.GONE
            setOnClickListener {
                if(!(application as VoyahIranApplication).subscriptions.vipAllowed)return@setOnClickListener
                LauncherPreferences.cycleClusterMap(this@VoyahLauncherActivity)
                preview?.refreshCluster()
                updateClusterButton()
            }
        }
        firstPage.addView(clusterButton)
        fullscreenButton=MapFullscreenButton(this).apply {
            visibility=View.GONE
            setOnClickListener {preview?.open {preview?.stopMonitoring()}}
        }
        firstPage.addView(fullscreenButton)
        pager.addView(firstPage,FrameLayout.LayoutParams(-1,-1))
        pager.addView(vehiclePage,FrameLayout.LayoutParams(-1,-1))
        pager.onPage={updatePageWork()}
        pager.onMotion={moving->
            dashboard.controlMotionPaused=moving
            vehiclePage.transitioning=moving
            // Cache only the native vehicle page. Never flatten the live map SurfaceView.
            vehiclePage.setLayerType(if(moving)View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,null)
            updatePageWork()
        }
        vehiclePage.onCommand=::vehicleCommand
        vehiclePage.onDriveStateChanged={ drivePopup?.dismiss();drivePopup=null }
        vehiclePage.onDrive={
            if(vehiclePage.state.driveMenuEnabled) {
                drivePopup?.dismiss()
                drivePopup=LauncherDrivePopup.show(vehiclePage,vehiclePage.state,vehiclePage.lightTheme) { mode -> vehicleCommand("drive",mode.toString()) }
            }
        }
        dashboard.onMap = { preview?.open { preview?.stopMonitoring() } }
        dashboard.onMapTouch={event,w,h->preview?.touch(event,w,h);Unit}
        dashboard.onShowMap = { preview?.showInCard() }
        dashboard.onMedia = media@{ button ->
            if (!dashboard.media.playing && (dashboard.media.controller == null || dashboard.media.title=="no music")) {
                if(defaultPlayJob?.isActive!=true) defaultPlayJob=lifecycleScope.launch {
                    try { com.abrar.voyahiran.feature.media.RahaMediaControl.playDefault(this@VoyahLauncherActivity) }
                    catch(e:CancellationException) { throw e }
                    catch(e:Exception) { com.abrar.voyahiran.core.logging.AppLog.error("LauncherMedia","default play failed",e) }
                }
                return@media
            }
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
        contentReady=true
        updateTheme()
        layoutContent()
        startupCover.bringToFront()
        pager.afterFrame {
            if(isFinishing || isDestroyed)return@afterFrame
            contentFrameReady=true
            com.abrar.voyahiran.core.logging.AppLog.info("LauncherWake", "Dashboard frame drawn; starting reveal")
            layoutContent()
            fadeStartupCover()
            if(visible)startVisibleWork()
            LauncherAppsCache.warm(this)
            PassengerArtworkCache.warm(this)
            vehiclePage.preload(lifecycleScope)
        }
    }
    private fun fadeStartupCover() {
        startupCover.animate().alpha(0f).setDuration(320)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction { container.removeView(startupCover) }.start()
    }
    private fun View.afterFrame(action:()->Unit) {
        val listener=object:android.view.ViewTreeObserver.OnDrawListener {
            var posted=false
            override fun onDraw() {
                if(posted)return
                posted=true
                post {
                    if(viewTreeObserver.isAlive)viewTreeObserver.removeOnDrawListener(this)
                    action()
                }
            }
        }
        viewTreeObserver.addOnDrawListener(listener)
        invalidate()
    }
    @Suppress("DEPRECATION") private fun layoutContent() {
        if(container.width==0)return
        // A patched activity already has the safe bounds. Only inset when the window is still full-display.
        val real=Point();windowManager.defaultDisplay.getRealSize(real)
        val location=IntArray(2);container.getLocationOnScreen(location)
        val left=(145f*real.x/1920-location[0]).toInt().coerceAtLeast(0)
        val top=(50f*real.y/720-location[1]).toInt().coerceAtLeast(0)
        val width=container.width-left
        // Card gutters are 16 design pixels; leave half that beneath both HOME pages.
        val bottomGap=kotlin.math.round(16f*width/1775f).toInt()
        val height=(container.height-top-bottomGap).coerceAtLeast(1)
        val lp=FrameLayout.LayoutParams(width,height).apply{leftMargin=left;topMargin=top}
        if(pager.parent==null)container.addView(pager,lp) else pager.layoutParams=lp
        if(preview==null && contentFrameReady) {
            val map=SurfaceView(this)
            mapView=map
            map.clipToOutline=true
            map.outlineProvider=object:android.view.ViewOutlineProvider(){override fun getOutline(v:View,o:android.graphics.Outline){o.setRoundRect(0,0,v.width,v.height,16f)}}
            val navigator=NavigationPreferences(this).navigator
            activeNavigator=navigator
            preview=LauncherMapPreview(this,lifecycleScope,{ dashboard.mapMessage=it;updateClusterButton() },map).also {it.packageName(navigator)}
            map.holder.addCallback(preview!!)
            preview?.startMonitoring()
            firstPage.addView(map,0,FrameLayout.LayoutParams((width*871f/1775).toInt(),height))
        } else mapView?.layoutParams=FrameLayout.LayoutParams((width*871f/1775).toInt(),height)
        val buttonSize=(64f*1.3f*width/1775f).toInt().coerceAtLeast(1)
        clusterButton.layoutParams=FrameLayout.LayoutParams(buttonSize,buttonSize).apply {
            leftMargin=(18f*width/1775f).toInt()
            topMargin=(height*.25f-buttonSize/2f-50f*width/1775f).toInt().coerceAtLeast(0)
        }
        fullscreenButton.layoutParams=FrameLayout.LayoutParams(buttonSize,buttonSize).apply {
            leftMargin=(18f*width/1775f).toInt()
            topMargin=(height*.25f+buttonSize/2f-38f*width/1775f).toInt().coerceAtLeast(0)
        }
        updateClusterButton()
    }
    private fun updateClusterButton() {
        fullscreenButton.visibility=if(dashboard.mapMessage.isEmpty()) View.VISIBLE else View.GONE
        fullscreenButton.light=LauncherTheme.isLight()
        clusterButton.visibility=if(dashboard.mapMessage.isEmpty() && LauncherPreferences.clusterMap(this) &&
            (application as VoyahIranApplication).subscriptions.vipAllowed) View.VISIBLE else View.GONE
        clusterButton.mode=LauncherPreferences.clusterMapMode(this)
        clusterButton.light=LauncherTheme.isLight()
    }
    override fun onResume() {
        super.onResume()
        val enabled=LauncherPreferences.enabled(this)
        val subscribed=(application as VoyahIranApplication).subscriptions.vipAllowed
        val screen=window.decorView.display?.displayId ?:0
        com.abrar.voyahiran.core.logging.AppLog.info("LauncherHome","resume enabled=$enabled subscription=$subscribed display=$screen")
        if(!enabled || !subscribed || screen!=0) {finish();return}
        val returning=!visible
        visible=true
        if(contentFrameReady) {
            if(returning) {
                startupCover.animate().cancel()
                startupCover.alpha=1f
                startupCover.setBackgroundColor(if(LauncherTheme.isLight())Color.WHITE else Color.BLACK)
                if(startupCover.parent==null)container.addView(startupCover,FrameLayout.LayoutParams(-1,-1))
                startupCover.bringToFront()
                container.afterFrame { if(visible && !isFinishing && !isDestroyed)fadeStartupCover() }
            }
            startVisibleWork()
        }
    }
    private fun startVisibleWork() {
        if(preview!=null && activeNavigator!=NavigationPreferences(this).navigator) {
            preview?.close();preview=null
            mapView?.let(firstPage::removeView);mapView=null
            dashboard.mapMessage="نقشه در دسترس نیست"
            layoutContent()
        }
        preview?.startMonitoring()
        updatePageWork()
        updateTheme()
        weather.start()
        mediaObserver.start()
        ticker?.cancel()
        ticker=lifecycleScope.launch {
            while(isActive) {
                updateTheme()
                updateClusterButton()
                // Animate elapsed time locally; playback changes arrive through callbacks.
                val state=mediaSnapshot
                if(state.playing && state.duration>0) {
                    val elapsed=android.os.SystemClock.elapsedRealtime()-mediaAt
                    val speed=state.controller?.playbackState?.playbackSpeed ?: 1f
                    dashboard.media=dashboard.media.copy(position=(state.position+(elapsed*speed).toLong()).coerceIn(0,state.duration))
                }
                if(!LauncherPreferences.enabled(this@VoyahLauncherActivity) || !(application as VoyahIranApplication).subscriptions.vipAllowed) { finish();break }
                delay(1000)
            }
        }
    }
    private fun updateTheme() {
        val light=LauncherTheme.isLight()
        if(vehiclePage.lightTheme!=light) { drivePopup?.dismiss();drivePopup=null }
        dashboard.lightTheme=light
        vehiclePage.lightTheme=light
        LauncherWindowTheme.apply(window,light)
        container.setBackgroundColor(LauncherWindowTheme.background(light))
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if(contentReady)updateTheme()
    }
    // Android 9 may pause the host when the map resumes on its virtual display; it is still visible.
    private fun updatePageWork() {
        if(!contentReady)return
        if(pager.moving && visible){
            // Keep existing subscriptions/cache alive. Registering/unregistering GPS
            // during ACTION_MOVE would itself add synchronous Binder work to a frame.
            vehiclePage.artwork.stop();vehiclePage.stopAnimations()
            return
        }
        mapPageVisible=visible && pager.page==0
        if(visible && pager.page==1) {
            // Keep the map surface alive offscreen: the pager can expose it during either swipe.
            // Only leaving the Activity releases it; ownership monitoring stays current on page two.
            compass.start();vehiclePage.artwork.start(lifecycleScope)
            if(vehicleTicker?.isActive==true)return
            vehicleTicker=lifecycleScope.launch {
                var revision=-1L
                while(isActive) {
                    val r=withContext(Dispatchers.IO) {com.abrar.voyahiran.core.bridge.BridgeClient.run(listOf("launcher-vehicle","watch",revision.toString()),4)}
                    if(r.exitCode==0 && !r.timedOut) {
                        runCatching {val json=org.json.JSONObject(r.output);revision=json.optLong("revision",-1);acceptVehicleSnapshot(json)}
                            .onFailure {if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000){vehicleCache.stale();vehiclePage.state=vehicleCache.display}}
                    } else if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000){vehicleCache.stale();vehiclePage.state=vehicleCache.display}
                    compass.expire()
                    delay(if(r.exitCode==0)100 else 1000)
                }
            }
        } else {
            vehicleTicker?.cancel();vehicleTicker=null;if(compassDelegate.isInitialized())compass.stop();vehiclePage.artwork.stop();vehiclePage.stopAnimations()
            if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000){vehicleCache.stale();vehiclePage.state=vehicleCache.display}
            if(visible)preview?.startMonitoring()
        }
    }
    private fun vehicleCommand(op:String,value:String) {
        if(!visible || pager.page!=1 || !vehiclePage.state.fresh)return
        if(!(application as VoyahIranApplication).subscriptions.vipAllowed)return
        val request=vehicleCache.request(op,value)
        request.token.dispatched=false
        vehiclePage.state=vehicleCache.display
        LauncherControlCommands.launch(lifecycleScope,listOf("launcher-vehicle",op,value),
            pending={vehicleCache.pending(request)},finish={vehicleCache.finish(request)},
            refresh={s->acceptVehicleSnapshot(s)},
            changed={vehiclePage.state=vehicleCache.display},
            failed={if(visible)android.widget.Toast.makeText(this,"تغییر توسط خودرو تأیید نشد",android.widget.Toast.LENGTH_LONG).show()},
            allowed={visible && (application as VoyahIranApplication).subscriptions.vipAllowed && vehiclePage.state.fresh},
            dispatching={request.token.dispatched=true},
            timeout=if(op=="height")30000L else LauncherControlCommands.FEEDBACK_MS)
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);if(contentReady)pager.show(0)}
    override fun onStop(){visible=false;updatePageWork();preview?.hostStopped();if(contentReady){weather.stop();mediaObserver.stop()};coverJob?.cancel();ticker?.cancel();super.onStop()}
    override fun onDestroy(){startupCover.animate().cancel();if(contentReady)vehiclePage.artwork.close();if(compassDelegate.isInitialized())compass.stop();preview?.close();preview=null;super.onDestroy()}
}
