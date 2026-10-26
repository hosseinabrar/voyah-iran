package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Same direct SurfaceHolder output as the vehicle-tested virtual-screen experiment.
 * No SurfaceControl reparenting and no input portal: card taps belong to the launcher.
 */
internal class LauncherEmbeddedMapSurface(private val context:Context,private val view:SurfaceView) {
    companion object { @Volatile var automationDisplayId: Int? = null; private set }
    private var display:VirtualDisplay?=null
    private var background:android.media.ImageReader?=null
    private var bufferWidth=0
    private var bufferHeight=0
    private var relay: MapFrameRelay?=null
    private var initializing:MapFrameRelay?=null
    private fun safeCleanup(action:()->Unit) { runCatching(action).onFailure {
        com.abrar.voyahiran.core.logging.AppLog.error("ClusterMap","Recovery cleanup failed",it)
    } }
    private fun failed(attempt:Int,failure:Throwable) {
        if(closed || attempt!=generation)return
        generation++
        val old=relay;relay=null
        val pending=initializing;initializing=null
        safeCleanup { display?.surface=null }
        safeCleanup { old?.let {LauncherClusterMap.hide(it)} }
        safeCleanup { old?.close() }
        safeCleanup { pending?.close() }
        safeCleanup { display?.surface=cardHolder?.surface?.takeIf {it.isValid} ?: background?.surface }
        retryRelay()
        com.abrar.voyahiran.core.logging.AppLog.error("ClusterMap","GPU relay failed; recovery scheduled",failure)
    }
    private val retry=ClusterMapRetry()
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var retryAt=0L
    private var wanted=false
    private var closed=false
    private var generation=0
    private val retryRun=Runnable { if(!closed && wanted) cluster(true) }
    private fun retryRelay() {
        val wait=retry.nextDelay()
        retryAt=android.os.SystemClock.elapsedRealtime()+wait
        handler.removeCallbacks(retryRun)
        if(!closed && wanted)handler.postDelayed(retryRun,wait)
    }
    private var cardHolder: SurfaceHolder?=null
    private var cardSurface:android.view.Surface?=null
    var attached=false;private set
    val displayId get()=display?.display?.displayId
    fun attach(holder:SurfaceHolder,width:Int,height:Int) {
        require(width>0 && height>0 && holder.surface.isValid)
        // surfaceCreated, surfaceChanged and onResume can report the same output.
        // Reconnecting it would tear down the relay's EGL window and discard its frame.
        // surfaceDestroyed clears attached even when Android reuses the Surface wrapper.
        if(attached && cardHolder===holder && cardSurface===holder.surface &&
            bufferWidth==width && bufferHeight==height)return
        cardHolder=holder
        val manager=context.getSystemService(DisplayManager::class.java)
        if(display==null) {
            display=manager.createVirtualDisplay("VoyahIran.MapPreview",width,height,160,relay?.source ?: holder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            checkNotNull(display) {"Virtual display creation failed"}
        } else {
            display!!.surface=relay?.source ?: holder.surface
            if(bufferWidth!=width || bufferHeight!=height) display!!.resize(width,height,160)
        }
        bufferWidth=width;bufferHeight=height
        background?.close();background=null
        attached=true
        cardSurface=holder.surface
        relay?.let {it.resize(width,height);it.card(holder.surface,width,height)}
    }
    fun cluster(inCard: Boolean) {
        wanted=inCard
        if(closed)return
        if(!inCard)handler.removeCallbacks(retryRun)
        if(!LauncherClusterMap.available(context)) {relay?.let {LauncherClusterMap.hide(it,"feature_unavailable")};return}
        if(!inCard) {relay?.let {LauncherClusterMap.cardPresence(it,false)};return}
        if(relay==null && android.os.SystemClock.elapsedRealtime()<retryAt) {
            handler.removeCallbacks(retryRun)
            handler.postDelayed(retryRun,(retryAt-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
            return
        }
        if(relay==null && initializing==null && android.os.SystemClock.elapsedRealtime()>=retryAt && attached && bufferWidth>0 && bufferHeight>0) {
            val attempt=++generation
            try {
                initializing=MapFrameRelay(context,bufferWidth,bufferHeight,
                    { failure -> handler.post { failed(attempt,failure) } },
                    { output -> handler.post {
                        if(closed || attempt!=generation || !wanted || !attached || !LauncherClusterMap.available(context)) {
                            if(initializing===output)initializing=null
                            safeCleanup {output.close()}
                        } else try {
                            display?.surface=null
                            relay=output;initializing=null
                            output.resize(bufferWidth,bufferHeight)
                            display?.surface=output.source
                            output.card(cardHolder?.surface,bufferWidth,bufferHeight)
                            LauncherClusterMap.show(context.applicationContext,output)
                        } catch(e:Exception) {failed(attempt,e)}
                    } })
            } catch(e:Exception) {
                failed(attempt,e)
            }
        }
        relay?.let {LauncherClusterMap.show(context.applicationContext,it)}
    }
    fun active(value:Boolean) {
        if(value && attached) automationDisplayId=displayId
        else if(automationDisplayId==displayId) automationDisplayId=null
        // Keep rendering during task handoff; detaching the producer mid-move can strand it.
    }
    fun detach() {
        cluster(false)
        relay?.card(null,0,0);cardHolder=null;cardSurface=null
        attached=false
        active(false)
        safeCleanup {display?.surface=null}
    }
    fun park() {
        cardHolder=null;cardSurface=null
        attached=false
        active(false)
        if(relay!=null) {relay?.card(null,0,0);return}
        if(display==null || background!=null || bufferWidth<=0 || bufferHeight<=0)return
        // SurfaceView's surface dies when HOME is covered. Keep a draining output
        // on the SAME display so the map task keeps rendering without a transfer.
        val reader=android.media.ImageReader.newInstance(bufferWidth,bufferHeight,android.graphics.PixelFormat.RGBA_8888,2)
        reader.setOnImageAvailableListener({ source ->
            runCatching { source.acquireLatestImage()?.close() }
        },android.os.Handler(android.os.Looper.getMainLooper()))
        try { display!!.surface=reader.surface;background=reader }
        catch(e:Exception) {reader.close();throw e}
    }
    fun close() {
        closed=true;generation++;handler.removeCallbacks(retryRun)
        safeCleanup {relay?.let {LauncherClusterMap.hide(it,"owner_closed")}}
        safeCleanup {detach()}
        safeCleanup {display?.release()};display=null
        safeCleanup {background?.close()};background=null
        safeCleanup {relay?.close()};relay=null
        safeCleanup {initializing?.close()};initializing=null
    }
}
