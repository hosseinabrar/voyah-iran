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
        require(command.parameters["seat"] == "driver" && command.parameters["slot"] == "driver")
        val operation = when (command.action) {
            "SAVE_SEAT_POSITION" -> "saveSeat01"
            "RECALL_SEAT_POSITION" -> "sendSeat01"
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
            val type = PathClassLoader(apk.path, javaClass.classLoader).loadClass("com.qinggan.bus.QGBusEvent")
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
            // The same Messenger preserves operation -> presentation ordering.
            // Presentation failure must never replay a save/recall operation.
            try {
                val displayEvent = type.getConstructor().newInstance()
                type.getField("mEventType").set(displayEvent, "SeatMemory")
                type.getField("mSource").set(displayEvent, context.packageName)
                type.getMethod("setData", Bundle::class.java).invoke(displayEvent,
                    Bundle().apply { putString("seatAction", "showSeatMemory") })
                remote.send(Message.obtain(null, 3).apply {
                    obj = Bundle().apply { putParcelable("event", displayEvent as Parcelable) }
                })
                com.abrar.voyahiran.core.logging.AppLog.info("RahaOemUi", "Seat memory window requested after $operation; physical completion is asynchronous")
            } catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("RahaOemUi", "Seat operation dispatched; window request failed, operation will not be repeated", e)
            }
            CommandResult(0, "OEM SeatMemory dispatched: $operation (Driving profile)")
        } catch (e: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("RahaSeat", "OEM memory dispatch failed", e)
            CommandResult(1, e.toString())
        } finally { if (bound) context.unbindService(connection) }
    }
}
