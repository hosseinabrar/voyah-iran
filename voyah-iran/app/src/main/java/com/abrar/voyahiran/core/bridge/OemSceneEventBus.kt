package com.abrar.voyahiran.core.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.abrar.voyahiran.core.logging.AppLog
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Uses the installed QGBus Parcelable and factory Messenger protocol, not process injection. */
internal object OemSceneEventBus {
    private val thread by lazy { HandlerThread("RahaSceneBus").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private var context: Context? = null // Accessed only on handler thread.

    fun publish(loader: ClassLoader, destination: String, event: String) {
        require(destination to event in setOf(
            "com.qinggan.app.campmode" to "qg_action_exit_camp_mode",
            "com.qinggan.app.restmode" to "qg_action_exit_rest_mode"
        ))
        val result = CompletableFuture<Unit>()
        handler.post {
            try {
                if (result.isDone) return@post
                val ctx = context ?: run {
                    val type = Class.forName("android.app.ActivityThread")
                    val activityThread = type.getMethod("systemMain").invoke(null)
                    (type.getMethod("getSystemContext").invoke(activityThread) as Context).also { context = it }
                }
                var bound = false
                lateinit var connection: ServiceConnection
                fun cleanup() { if (bound) { bound = false; ctx.unbindService(connection) } }
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        try {
                            if (result.isDone) return
                            val type = loader.loadClass("com.qinggan.bus.QGBusEvent")
                            val payload = type.getConstructor().newInstance()
                            type.getField("mSource").set(payload, "com.abrar.voyahiran")
                            type.getMethod("setDestination", String::class.java).invoke(payload, destination)
                            type.getMethod("setEventType", String::class.java).invoke(payload, event)
                            type.getMethod("setData", Bundle::class.java).invoke(payload, Bundle().apply { putString("key_src", "src_voice") })
                            val message = Message.obtain().apply {
                                what = 3
                                obj = Bundle().apply { putParcelable("event", payload as Parcelable) }
                            }
                            Messenger(binder).send(message)
                            AppLog.info("RahaScene", "QGBus dispatched destination=$destination event=$event")
                            result.complete(Unit)
                        } catch (e: Exception) { result.completeExceptionally(e) }
                        finally { cleanup() }
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        result.completeExceptionally(IllegalStateException("QGBus disconnected")); cleanup()
                    }
                    override fun onNullBinding(name: ComponentName) {
                        result.completeExceptionally(IllegalStateException("QGBus returned null binder")); cleanup()
                    }
                }
                val user = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType).invoke(null, 0)
                bound = Context::class.java.getMethod("bindServiceAsUser", Intent::class.java,
                    ServiceConnection::class.java, Int::class.javaPrimitiveType, UserHandle::class.java)
                    .invoke(ctx, Intent("com.qinggan.QGBus.QGBusService").setPackage("com.qinggan.QGBus"),
                        connection, Context.BIND_AUTO_CREATE, user) as Boolean
                check(bound) { "QGBus binding refused" }
                handler.postDelayed({
                    if (result.completeExceptionally(IllegalStateException("QGBus connection timed out"))) cleanup()
                }, 3000)
            } catch (e: Exception) { result.completeExceptionally(e) }
        }
        try { result.get(4, TimeUnit.SECONDS) }
        catch (e: java.util.concurrent.TimeoutException) {
            result.completeExceptionally(e) // Do not publish a late event after reporting failure.
            throw e
        }
    }
}
