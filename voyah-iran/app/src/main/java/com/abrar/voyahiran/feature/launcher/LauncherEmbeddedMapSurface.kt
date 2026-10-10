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
    var attached=false;private set
    val displayId get()=display?.display?.displayId
    fun attach(holder:SurfaceHolder,width:Int,height:Int) {
        require(width>0 && height>0 && holder.surface.isValid)
        val manager=context.getSystemService(DisplayManager::class.java)
        if(display==null) {
            display=manager.createVirtualDisplay("VoyahIran.MapPreview",width,height,160,holder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            checkNotNull(display) {"Virtual display creation failed"}
        } else {
            display!!.surface=holder.surface
            if(bufferWidth!=width || bufferHeight!=height) display!!.resize(width,height,160)
        }
        bufferWidth=width;bufferHeight=height
        background?.close();background=null
        attached=true
    }
    fun active(value:Boolean) {
        if(value && attached) automationDisplayId=displayId
        else if(automationDisplayId==displayId) automationDisplayId=null
        // Keep rendering during task handoff; detaching the producer mid-move can strand it.
    }
    fun detach() {
        attached=false
        active(false)
        display?.surface=null
    }
    fun park() {
        attached=false
        active(false)
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
    fun close() {detach();display?.release();display=null;background?.close();background=null}
}
