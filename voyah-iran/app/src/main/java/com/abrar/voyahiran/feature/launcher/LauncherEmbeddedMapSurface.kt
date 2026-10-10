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
            display!!.resize(width,height,160)
        }
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
    fun close() {detach();display?.release();display=null}
}
