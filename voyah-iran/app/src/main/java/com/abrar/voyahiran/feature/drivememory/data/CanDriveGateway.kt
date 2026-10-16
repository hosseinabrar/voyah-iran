package com.abrar.voyahiran.feature.drivememory.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.abrar.voyahiran.feature.drivememory.domain.VehicleSample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

enum class CanField { MODE, RECOVERY, AEB, PEDESTRIAN, ENERGY_MODE, ACC, READY, SPEED, DISCONNECTED }
data class CanSignal(val field: CanField, val value: Int)

/** Read-only adapter for the supplied H97x CAN protocol. No raw frames or write transaction. */
class CanDriveGateway(context: Context, private val signal: (CanSignal) -> Unit) {
    private val context = context.applicationContext
    @Volatile private var remote: IBinder? = null
    private var connection: ServiceConnection? = null
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CALLBACK); return true }
            data.enforceInterface(CALLBACK)
            when (code) {
                0x24 -> if (data.readInt() != 0) {
                    val ordinal = data.readInt()
                    val id = data.readInt()
                    val value = data.readInt()
                    if (ordinal == ENERGY_MODE_ORDINAL && id == ENERGY_MODE_ID) signal(CanSignal(CanField.ENERGY_MODE, value))
                    if (ordinal == PEDESTRIAN_ORDINAL && id == PEDESTRIAN_ID) signal(CanSignal(CanField.PEDESTRIAN, value))
                    if (ordinal == AEB_ORDINAL && id == AEB_ID) signal(CanSignal(CanField.AEB, value))
                    if (ordinal == MODE_ORDINAL && id == MODE_ID) signal(CanSignal(CanField.MODE, value))
                    if (ordinal == READY_ORDINAL && id == READY_ID) signal(CanSignal(CanField.READY, value))
                    if (ordinal == RECOVERY_ORDINAL && id == RECOVERY_ID) signal(CanSignal(CanField.RECOVERY, value))
                }
                0x27 -> signal(CanSignal(CanField.ACC, data.readInt()))
                0x10 -> signal(CanSignal(CanField.SPEED, data.readInt()))
            }
            // The callback interface is oneway. Unrelated CAN fields are deliberately ignored.
            return true
        }
    }

    suspend fun read(): VehicleSample {
        connect()
        return VehicleSample(readState(MODE_ORDINAL, MODE_ID), readInt(0x42), readState(READY_ORDINAL, READY_ID), readInt(0x1A), readState(RECOVERY_ORDINAL, RECOVERY_ID), runCatching { readState(AEB_ORDINAL, AEB_ID) }.getOrNull(),
            runCatching { readState(PEDESTRIAN_ORDINAL, PEDESTRIAN_ID) }.getOrNull(),
            runCatching { readState(ENERGY_MODE_ORDINAL, ENERGY_MODE_ID) }.getOrNull())
    }

    suspend fun readRearCameraStatus(): Int {
        connect()
        return readState(0x2C3, 0x39C)
    }

    /** ICanBusService.getDoorStatus (2): presence, bonnet, front-left, ... */
    suspend fun readDriverDoor(): Int {
        connect()
        return call(2, {}) {
            if (readInt() == 0) -1 else {
                readInt() // bonnet; do not confuse it with the driver's door
                readInt().takeIf { it in 0..1 } ?: -1
            }
        }
    }

    suspend fun readReportState(ordinal: Int, id: Int): Int {
        connect()
        return readState(ordinal, id)
    }

    private suspend fun connect() {
        if (remote?.isBinderAlive == true) return
        close()
        val ready = CompletableDeferred<IBinder>()
        val pending = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { ready.complete(service) }
            override fun onServiceDisconnected(name: ComponentName) {
                com.abrar.voyahiran.core.logging.AppLog.info("CAN", "disconnected")
                remote = null
                signal(CanSignal(CanField.DISCONNECTED, 0))
                ready.completeExceptionally(IllegalStateException("CAN disconnected"))
            }
            override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IllegalStateException("CAN unavailable")) }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        try {
            connection = pending
            check(context.bindService(Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.CanBusService")), pending, Context.BIND_AUTO_CREATE))
            val service = withTimeout(3000) { ready.await() }
            check(service.interfaceDescriptor == DESCRIPTOR) { "Unsupported CAN interface" }
            remote = service
            com.abrar.voyahiran.core.logging.AppLog.info("CAN", "connected descriptor=${service.interfaceDescriptor}")
            check(call(0x1C, { writeStrongBinder(callback) }) { readInt() != 0 }) { "CAN callback registration denied" }
        } catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("CAN", "connect-failed", error)
            close(); throw error
        }
    }

    fun close() {
        if (remote?.isBinderAlive == true) runCatching { call(0x1D, { writeStrongBinder(callback) }) { readInt() } }
        remote = null
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    private fun readState(ordinal: Int, id: Int) = call(0x39, { writeInt(1); writeInt(ordinal); writeInt(id) }) { readInt() }
    private fun readInt(code: Int) = call(code, {}) { readInt() }

    private fun <T> call(code: Int, write: Parcel.() -> Unit, read: Parcel.() -> T): T {
        val request = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR); request.write()
            check(remote?.transact(code, request, reply, 0) == true) { "CAN transaction unavailable" }
            reply.readException()
            return reply.read()
        } catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("CAN", "transaction=$code failed", error)
            throw error
        } finally { reply.recycle(); request.recycle() }
    }

    companion object {
        // DFVehicleState.AEB_SWITCH aliases VehicleState.AEBSwitch, NOT VehicleState.AEB_SWITCH.
        const val ENERGY_MODE_ORDINAL = 0x2e4
        const val ENERGY_MODE_ID = 0x3bd
        const val PEDESTRIAN_ORDINAL = 0x1bf
        const val PEDESTRIAN_ID = 0x299
        const val AEB_ORDINAL = 0x3e
        const val AEB_ID = 0x92
        const val MODE_ORDINAL = 0x148
        const val MODE_ID = 0x221
        const val READY_ORDINAL = 0x2C1
        const val READY_ID = 0x39C
        const val RECOVERY_ORDINAL = 0x237
        const val RECOVERY_ID = 0x312
        private const val PACKAGE = "com.qinggan.canbus.service"
        private const val DESCRIPTOR = "com.qinggan.canbus.ICanBusService"
        private const val CALLBACK = "com.qinggan.canbus.ICanBusServiceCallback"
    }
}
