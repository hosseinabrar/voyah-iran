package com.abrar.voyahiran.feature.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import com.abrar.voicecommands.VehicleCommand
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File

/** Uses SeatMemoryManager's interprocess event so OEM saved positions/UI stay consistent. */
object OemSeatMemory {
    suspend fun execute(context: Context, command: VehicleCommand): CommandResult {
        require(command.parameters["seat"] == "driver")
        val slot = when (command.parameters["slot"]?.toString()) {
            null, "driver", "1" -> "01"
            "2" -> "02"
            "3" -> "03"
            else -> error("Unsupported seat memory slot")
        }
        val operation = when (command.action) {
            "SAVE_SEAT_POSITION" -> "saveSeat$slot"
            "RECALL_SEAT_POSITION" -> "sendSeat$slot"
            else -> error("Unsupported seat operation")
        }
        val connected = CompletableDeferred<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.complete(service) }
            override fun onServiceDisconnected(name: ComponentName) { connected.completeExceptionally(IllegalStateException("OEM event bus disconnected")) }
            override fun onNullBinding(name: ComponentName) { connected.completeExceptionally(IllegalStateException("OEM event bus unavailable")) }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        var bound = false
        return try {
            val apk = File("/system/priv-app/VehicleAir-release-signed/VehicleAir-release-signed.apk")
            check(apk.isFile) { "OEM seat application unavailable" }
            // Parent-first loading can select the framework's hidden QGAPI class instead
            // of the public Parcelable shipped in VehicleAir. Load only this event family
            // from that APK; Android classes (Bundle/Parcelable) retain parent delegation.
            val loader = object : PathClassLoader(apk.path, javaClass.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name == "com.qinggan.bus.QGBusEvent" || name.startsWith("com.qinggan.bus.QGBusEvent\$")) {
                        synchronized(this) {
                            val loaded = findLoadedClass(name) ?: findClass(name)
                            if (resolve) resolveClass(loaded)
                            return loaded
                        }
                    }
                    return super.loadClass(name, resolve)
                }
            }
            val type = loader.loadClass("com.qinggan.bus.QGBusEvent")
            com.abrar.voyahiran.core.logging.AppLog.info("RahaSeat",
                "event source=${apk.path} loader=${type.classLoader} constructors=${type.declaredConstructors.joinToString { it.toGenericString() }}")
            val event = type.getConstructor().newInstance()
            type.getField("mEventType").set(event, "SeatMemory")
            type.getField("mSource").set(event, context.packageName)
            type.getMethod("setData", Bundle::class.java).invoke(event, Bundle().apply { putString("seatAction", operation) })
            bound = context.bindService(Intent("com.qinggan.QGBus.QGBusService").setPackage("com.qinggan.QGBus"), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "OEM event bus binding denied" }
            val remote = Messenger(withTimeout(3000) { connected.await() })
            remote.send(Message.obtain(null, 3).apply {
                obj = Bundle().apply { putParcelable("event", event as Parcelable) }
            })
            // Presentation has its own bounded lifetime; failure must never replay the operation.
            try {
                val presentation = com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                    listOf("su", "0", "raha-seat-ui", "show"), 5)
                check(presentation.exitCode == 0 && !presentation.timedOut) { presentation.output }
                com.abrar.voyahiran.core.logging.AppLog.info("RahaOemUi", "Seat popup scheduled after $operation: ${presentation.output}")
            } catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("RahaOemUi", "Seat operation dispatched; popup failed, operation will not be repeated", e)
            }
            CommandResult(0, "OEM SeatMemory dispatched: $operation (slot=$slot)")
        } catch (e: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("RahaSeat", "OEM memory dispatch failed", e)
            CommandResult(1, e.toString())
        } finally { if (bound) context.unbindService(connection) }
    }
}
