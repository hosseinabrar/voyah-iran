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
    private lateinit var dashboard: LauncherDashboard
    private lateinit var container: FrameLayout
    private lateinit var pager: LauncherPager
    private lateinit var firstPage: FrameLayout
    private lateinit var vehiclePage: LauncherVehicleDashboard
    private var vehicleTicker: Job? = null
    private var visible=false
    private val commandJobs=mutableMapOf<String,Job>()
    private val vehicleCache=LauncherVehicleCache()
    private var lastVehicleRead=0L
    private val compass by lazy { LauncherCompass(this) {vehiclePage.compass=it} }
    private var preview: LauncherMapPreview? = null
    private var mapView: SurfaceView? = null
    private var activeNavigator = ""
    private var ticker: Job? = null
    private var defaultPlayJob: Job? = null
    private var coverJob: Job? = null
    private var mediaAt=0L
    private var mediaSnapshot=LauncherMediaState()
    private val mediaObserver by lazy { LauncherMediaObserver(this,lifecycleScope) { state ->
        mediaSnapshot=state;mediaAt=android.os.SystemClock.elapsedRealtime()
        dashboard.media=state
        coverJob?.cancel()
        if(state.cover==null && state.coverUri.isNotBlank()) coverJob=lifecycleScope.launch {
            val cover=withContext(Dispatchers.IO) { artwork.load(state.coverUri) }
            dashboard.media=dashboard.media.copy(cover=cover)
        }
    } }
    private val weather by lazy { LauncherWeather(this,lifecycleScope) { dashboard.weather=it } }
    private val artwork by lazy { LauncherArtwork(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        container=FrameLayout(this).apply { setBackgroundColor(Color.BLACK); layoutDirection=View.LAYOUT_DIRECTION_LTR }
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
        pager.addView(firstPage,FrameLayout.LayoutParams(-1,-1))
        pager.addView(vehiclePage,FrameLayout.LayoutParams(-1,-1))
        pager.onPage={updatePageWork()}
        vehiclePage.onCommand=::vehicleCommand
        vehiclePage.onDrive={
            val options=LauncherVehicleState.driveNames.entries.filter {it.key!=4 || vehiclePage.state.supportsOuting}
            android.app.AlertDialog.Builder(this)
                .setTitle("Driving mode")
                .setSingleChoiceItems(options.map{it.value}.toTypedArray(),options.indexOfFirst{it.key==vehiclePage.state.drive}) { dialog,index ->
                    dialog.dismiss();vehicleCommand("drive",options[index].key.toString())
                }.setNegativeButton(android.R.string.cancel,null).show()
        }
        setContentView(container)
        dashboard.onMap = { preview?.open { preview?.stopMonitoring() } }
        dashboard.onShowMap = { preview?.showInCard() }
        dashboard.onMedia = media@{ button ->
            if (!dashboard.media.playing && dashboard.media.controller == null) {
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
        if(pager.parent==null)container.addView(pager,lp) else pager.layoutParams=lp
        if(preview==null) {
            val map=SurfaceView(this)
            mapView=map
            map.clipToOutline=true
            map.outlineProvider=object:android.view.ViewOutlineProvider(){override fun getOutline(v:View,o:android.graphics.Outline){o.setRoundRect(0,0,v.width,v.height,16f)}}
            val navigator=NavigationPreferences(this).navigator
            activeNavigator=navigator
            preview=LauncherMapPreview(this,lifecycleScope,{ dashboard.mapMessage=it },map).also {it.packageName(navigator)}
            map.holder.addCallback(preview!!)
            preview?.startMonitoring()
            firstPage.addView(map,0,FrameLayout.LayoutParams((width*871f/1775).toInt(),height))
        } else mapView?.layoutParams=FrameLayout.LayoutParams((width*871f/1775).toInt(),height)
    }
    override fun onResume() {
        super.onResume()
        val enabled=LauncherPreferences.enabled(this)
        val subscribed=(application as VoyahIranApplication).subscriptions.allowed
        val screen=window.decorView.display?.displayId ?:0
        com.abrar.voyahiran.core.logging.AppLog.info("LauncherHome","resume enabled=$enabled subscription=$subscribed display=$screen")
        if(!enabled || !subscribed || screen!=0) {finish();return}
        if(preview!=null && activeNavigator!=NavigationPreferences(this).navigator) {
            preview?.close();preview=null
            mapView?.let(firstPage::removeView);mapView=null
            dashboard.mapMessage="نقشه در دسترس نیست"
            layoutContent()
        }
        preview?.startMonitoring()
        visible=true
        updatePageWork()
        updateTheme()
        weather.start()
        mediaObserver.start()
        ticker?.cancel()
        ticker=lifecycleScope.launch {
            while(isActive) {
                updateTheme()
                // Animate elapsed time locally; playback changes arrive through callbacks.
                val state=mediaSnapshot
                if(state.playing && state.duration>0) {
                    val elapsed=android.os.SystemClock.elapsedRealtime()-mediaAt
                    val speed=state.controller?.playbackState?.playbackSpeed ?: 1f
                    dashboard.media=dashboard.media.copy(position=(state.position+(elapsed*speed).toLong()).coerceIn(0,state.duration))
                }
                if(!LauncherPreferences.enabled(this@VoyahLauncherActivity) || !(application as VoyahIranApplication).subscriptions.allowed) { finish();break }
                delay(1000)
            }
        }
    }
    private fun updateTheme() {
        val light=LauncherTheme.isLight()
        dashboard.lightTheme=light
        vehiclePage.lightTheme=light
        container.setBackgroundColor(if(light)0xffe5ebf3.toInt() else Color.BLACK)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTheme()
    }
    // Android 9 may pause the host when the map resumes on its virtual display; it is still visible.
    private fun updatePageWork() {
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
                        runCatching {val json=org.json.JSONObject(r.output);revision=json.optLong("revision",-1);LauncherVehicleState.parse(json)}.onSuccess {
                            lastVehicleRead=android.os.SystemClock.elapsedRealtime();vehicleCache.accept(it);vehiclePage.state=vehicleCache.display;compass.speed=it.speed
                        }.onFailure {if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000)vehiclePage.state=vehiclePage.state.copy(fresh=false)}
                    } else if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000)vehiclePage.state=vehiclePage.state.copy(fresh=false)
                    compass.expire()
                    delay(if(r.exitCode==0)100 else 1000)
                }
            }
        } else {
            vehicleTicker?.cancel();vehicleTicker=null;compass.stop();vehiclePage.artwork.stop();vehiclePage.stopAnimations()
            if(android.os.SystemClock.elapsedRealtime()-lastVehicleRead>15000)vehiclePage.state=vehiclePage.state.copy(fresh=false)
            if(visible)preview?.startMonitoring()
        }
    }
    private fun vehicleCommand(op:String,value:String) {
        if(!visible || pager.page!=1 || !vehiclePage.state.fresh)return
        if(!(application as VoyahIranApplication).subscriptions.allowed)return
        val key=if(op=="height" || op=="drive")"drive-height" else op
        commandJobs.remove(key)?.cancel()
        val request=vehicleCache.request(op,value)
        vehiclePage.state=vehicleCache.display
        commandJobs[key]=lifecycleScope.launch {
            try {
                val r=withContext(Dispatchers.IO) {com.abrar.voyahiran.core.bridge.BridgeClient.run(listOf("launcher-vehicle",op,value),12)}
                check(r.exitCode==0 && !r.timedOut) {r.output}
                // Readback arrives through the single shared reader, never a second polling loop.
                val confirmed=withTimeoutOrNull(if(op=="height")30000L else 10000L) {
                    while(vehicleCache.pending(request))delay(100)
                    true
                } ?: false
                if(!confirmed)error("Vehicle feedback was not confirmed")
            } catch(e:CancellationException){throw e}
            catch(e:Exception){
                com.abrar.voyahiran.core.logging.AppLog.error("LauncherVehicle","$op=$value",e)
                if(visible)android.widget.Toast.makeText(this@VoyahLauncherActivity,"تغییر توسط خودرو تأیید نشد",android.widget.Toast.LENGTH_LONG).show()
            } finally {vehicleCache.finish(request);vehiclePage.state=vehicleCache.display}
        }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);pager.show(0)}
    override fun onStop(){visible=false;updatePageWork();preview?.hostStopped();weather.stop();mediaObserver.stop();coverJob?.cancel();ticker?.cancel();super.onStop()}
    override fun onDestroy(){vehiclePage.artwork.close();compass.stop();preview?.close();preview=null;super.onDestroy()}
}
