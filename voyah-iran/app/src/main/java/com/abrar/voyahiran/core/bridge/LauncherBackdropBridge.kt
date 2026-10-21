package com.abrar.voyahiran.core.bridge

import android.content.Context
import android.database.ContentObserver
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.launcher.LauncherTheme
import com.abrar.voyahiran.feature.launcher.LauncherWindowTheme

/** Root compositor color layers, NOT windows/tasks. No focus, input, or activity identity. */
internal object LauncherBackdropBridge {
    private val thread by lazy { HandlerThread("LauncherBackdrop").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private var context: Context? = null
    private var observing = false
    private var started = false
    private val layers = mutableMapOf<Int, Pair<BackdropSpec, Any>>()
    private val surface by lazy { Class.forName("android.view.SurfaceControl") }
    private val transaction by lazy { Class.forName("android.view.SurfaceControl\$Transaction") }
    private val builder by lazy { Class.forName("android.view.SurfaceControl\$Builder") }
    private val refresh = Runnable { reconcile() }

    @Synchronized fun start() {
        if (started) return
        started = true
        handler.post(object : Runnable {
            override fun run() {
                reconcile()
                // Lease expiry/recovery supervision only; unchanged layers do no drawing.
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun requestRefresh() {
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    private fun systemContext(): Context {
        context?.let { return it }
        val type = Class.forName("android.app.ActivityThread")
        val owner = type.getMethod("currentActivityThread").invoke(null)
            ?: type.getMethod("systemMain").invoke(null)
        return (type.getMethod("getSystemContext").invoke(owner) as Context).also { context = it }
    }

    private fun observe(c: Context) {
        if (observing) return
        c.contentResolver.registerContentObserver(Settings.Global.getUriFor("voyah_iran_launcher_home"), false,
            object : ContentObserver(handler) { override fun onChange(selfChange: Boolean) = requestRefresh() })
        c.getSystemService(DisplayManager::class.java).registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = requestRefresh()
            override fun onDisplayRemoved(id: Int) = requestRefresh()
            override fun onDisplayChanged(id: Int) = requestRefresh()
        }, handler)
        // OEM vehicle theme is a system property, independent of Android night mode.
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("addChangeCallback", Runnable::class.java)
                .invoke(null, Runnable { requestRefresh() })
        }
        observing = true
    }

    private fun reconcile() {
        try {
            val c = systemContext()
            observe(c)
            val enabled = Settings.Global.getInt(c.contentResolver, "voyah_iran_launcher_home", 0) == 1 &&
                BridgeSubscriptionSession.vipActive
            val desired = mutableMapOf<Int, BackdropSpec>()
            if (enabled) {
                val type = Class.forName("android.hardware.display.DisplayManagerGlobal")
                val manager = type.getMethod("getInstance").invoke(null)
                val color = LauncherWindowTheme.background(LauncherTheme.isLight())
                for (id in listOf(0, 1)) {
                    val info = type.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(manager, id) ?: continue
                    fun field(name: String) = info.javaClass.getField(name).getInt(info)
                    BackdropSpec.forDisplay(id, field("type"), field("layerStack"),
                        field("logicalWidth"), field("logicalHeight"), color)?.let { desired[id] = it }
                }
            }
            for (id in layers.keys.toList()) if (id !in desired) remove(id)
            for ((id, spec) in desired) {
                val old = layers[id]
                if (old?.first == spec) continue
                val layer = old?.second ?: builder.getConstructor().newInstance().let { b ->
                    builder.getMethod("setName", String::class.java).invoke(b, "VoyahIran.Backdrop.$id")
                    builder.getMethod("setColorLayer").invoke(b)
                    builder.getMethod("build").invoke(b)
                }
                try {
                    transact { t ->
                        transaction.getMethod("setLayerStack", surface, Int::class.javaPrimitiveType).invoke(t, layer, spec.stack)
                        transaction.getMethod("setLayer", surface, Int::class.javaPrimitiveType).invoke(t, layer, BackdropSpec.Z)
                        transaction.getMethod("setWindowCrop", surface, Rect::class.java).invoke(t, layer, Rect(0, 0, spec.width, spec.height))
                        transaction.getMethod("setColor", surface, FloatArray::class.java).invoke(t, layer, spec.rgb())
                        transaction.getMethod("show", surface).invoke(t, layer)
                    }
                    layers[id] = spec to layer
                    AppLog.info("LauncherBackdrop", "display=$id stack=${spec.stack} size=${spec.width}x${spec.height} color=${spec.color}; no window/task")
                } catch (e: Exception) {
                    if (old == null) surface.getMethod("release").invoke(layer)
                    throw e
                }
            }
        } catch (e: Exception) {
            AppLog.changed("LauncherBackdrop", "unavailable: ${e.cause ?: e}")
            // Never fall back to an Activity or overlay window; those alter task/input policy.
            for (id in layers.keys.toList()) runCatching { remove(id) }
        }
    }

    private fun transact(block: (Any) -> Unit) {
        val t = transaction.getConstructor().newInstance()
        try { block(t); transaction.getMethod("apply").invoke(t) }
        finally { transaction.getMethod("close").invoke(t) }
    }

    private fun remove(id: Int) {
        val layer = layers[id]?.second ?: return
        transact { transaction.getMethod("remove", surface).invoke(it, layer) }
        layers.remove(id)
        AppLog.info("LauncherBackdrop", "removed display=$id")
    }
}

internal data class BackdropSpec(val stack: Int, val width: Int, val height: Int, val color: Int) {
    fun rgb() = floatArrayOf(((color ushr 16) and 255) / 255f, ((color ushr 8) and 255) / 255f, (color and 255) / 255f)
    companion object {
        const val Z = Int.MIN_VALUE
        fun forDisplay(id: Int, type: Int, stack: Int, width: Int, height: Int, color: Int): BackdropSpec? =
            if (id in 0..1 && type in 1..2 && stack >= 0 && width > 0 && height > 0)
                BackdropSpec(stack, width, height, color) else null
    }
}
