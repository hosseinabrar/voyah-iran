package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View

/** The SurfaceControl display attachment used by the vehicle's VirtualDisplayTaskEmbedder.
 * No input forwarding or tap-exclusion region: the launcher owns all card gestures.
 */
internal class LauncherEmbeddedMapSurface(private val context:Context,private val view:SurfaceView) {
    companion object { @Volatile var automationDisplayId: Int? = null; private set }
    private var display:VirtualDisplay?=null
    var attached=false;private set
    val displayId get()=display?.display?.displayId
    fun attach(holder:SurfaceHolder,width:Int,height:Int) {
        require(width>0 && height>0 && holder.surface.isValid)
        attached=false
        val manager=context.getSystemService(DisplayManager::class.java)
        if(display==null) {
            // Unlike DESTROY_CONTENT_ON_REMOVAL, these flags preserve navigation on handoff.
            display=manager.createVirtualDisplay("VoyahIran.MapPreview",width,height,160,
                if(Build.VERSION.SDK_INT>=29)null else holder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            checkNotNull(display) {"Virtual display creation failed"}
        }
        display!!.resize(width,height,160)
        if(Build.VERSION.SDK_INT>=29) {
            // Process-local access to just the native embedding APIs; no global hidden-API setting.
            check(org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/view/View;->getWindow",
                "Landroid/view/SurfaceView;->getSurfaceControl",
                "Landroid/view/WindowManagerGlobal;->getWindow",
                "Landroid/view/IWindowSession;->reparentDisplayContent",
                "Landroid/view/IWindowManager;->dontOverrideDisplayInfo",
                "Landroid/view/InputWindowHandle;",
                "Landroid/view/SurfaceControl\$Transaction;",
                "Landroid/hardware/display/VirtualDisplay;->setDisplayState")) {"Native embedding API access unavailable"}
            val global=Class.forName("android.view.WindowManagerGlobal")
            val session=global.getMethod("getWindowSession").invoke(null)
            val window=View::class.java.getDeclaredMethod("getWindow").apply {isAccessible=true}.invoke(view)
            checkNotNull(window) {"Launcher window not attached"}
            val control=SurfaceView::class.java.getMethod("getSurfaceControl").invoke(view)
            Class.forName("android.view.IWindowSession").getMethod("reparentDisplayContent",
                Class.forName("android.view.IWindow"),Class.forName("android.view.SurfaceControl"),Int::class.javaPrimitiveType)
                .invoke(session,window,control,displayId!!)
            // reparentDisplayContent installs a native input portal. Replace it with a
            // non-touchable handle: the dashboard, not the embedded map, owns card taps.
            val handleType=Class.forName("android.view.InputWindowHandle")
            val handle=handleType.getConstructor(Class.forName("android.view.InputApplicationHandle"),Int::class.javaPrimitiveType)
                .newInstance(null,view.display.displayId)
            handleType.getField("name").set(handle,"VoyahIran read-only map")
            handleType.getField("layoutParamsFlags").setInt(handle,android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            val transactionType=Class.forName("android.view.SurfaceControl\$Transaction")
            val transaction=transactionType.getConstructor().newInstance()
            try {
                val controlType=Class.forName("android.view.SurfaceControl")
                transactionType.getMethod("setInputWindowInfo",controlType,handleType).invoke(transaction,control,handle)
                transactionType.getMethod("setWindowCrop",controlType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
                    .invoke(transaction,control,width,height)
                transactionType.getMethod("apply").invoke(transaction)
            } finally { transactionType.getMethod("close").invoke(transaction) }
            val wm=global.getMethod("getWindowManagerService").invoke(null)
            Class.forName("android.view.IWindowManager").getMethod("dontOverrideDisplayInfo",Int::class.javaPrimitiveType)
                .invoke(wm,displayId!!)
        } else {
            // Android 9 ActivityView uses the SurfaceHolder output directly.
            display!!.surface=holder.surface
        }
        attached=true
        active(true)
    }
    // Intentional OEM framework integration: attach() grants the process-local exemption
    // before this API is used. Callers handle unsupported firmware by hiding the preview.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    fun active(value:Boolean) {
        if(value && attached) automationDisplayId=displayId
        else if(automationDisplayId==displayId) automationDisplayId=null
        if(Build.VERSION.SDK_INT>=29) display?.let {
            VirtualDisplay::class.java.getDeclaredMethod("setDisplayState",Boolean::class.javaPrimitiveType)
                .apply {isAccessible=true}.invoke(it,value && attached)
        }
    }
    fun detach() {
        attached=false
        runCatching {active(false)}
        if(Build.VERSION.SDK_INT<29)display?.surface=null
    }
    fun close() {detach();display?.release();display=null}
}
