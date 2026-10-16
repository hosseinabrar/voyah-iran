package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import com.abrar.raha.RahaAction
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter
import com.abrar.voyahiran.feature.voice.RahaVehiclePlan
import com.abrar.voyahiran.feature.voice.RahaVehicleSnapshot
import com.abrar.voyahiran.feature.voice.RahaWrite
import com.abrar.voyahiran.feature.voice.WindowPositionController
import com.abrar.voyahiran.feature.voice.ClimateStateController
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File

/** Runs only inside the authenticated root bridge. Uses the installed OEM SDK's enums and parceling. */
object RahaVehicleBridge {
    /** Same SDK, field and planner as Raha; no UI, motor override, or retry on failure. */
    fun closeSunshadeOnDeparture(): CommandResult = try {
        check(BridgeSubscriptionSession.vipActive) { "VIP subscription inactive" }
        val sdk = OemSession()
        val plan = RahaVehiclePlan(sdk).create(RahaAction.SET_SUNSHADE_POSITION, mapOf("position" to 0))
        check(plan == listOf(RahaWrite("vehicle", "ROLL_OPEN_PERCENT", 0))) { "Unexpected shade plan" }
        plan.forEach(sdk::validate)
        val before = sdk.vehicle("ROLL_OPEN_PERCENT")
        check(before in 0..100) { "Sunshade position unavailable" }
        check(BridgeSubscriptionSession.vipActive && sdk.vehicle("HUM_VCU_READY") == 0 && sdk.speed() == 0) {
            "Departure closure requires VIP, READY off and stationary vehicle"
        }
        if (before != 0) {
            AppLog.info("SunshadeDeparture", "OEM request ROLL_OPEN_PERCENT=0 before=$before")
            sdk.write(plan.single())
            val deadline = android.os.SystemClock.elapsedRealtime() + 6000
            var actual = sdk.vehicle("ROLL_OPEN_PERCENT")
            while (actual != 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(150)
                actual = sdk.vehicle("ROLL_OPEN_PERCENT")
            }
            check(actual == 0) { "Sunshade closure not confirmed: $actual" }
        }
        CommandResult(0, "SUNSHADE_CLOSED")
    } catch (error: Exception) {
        AppLog.error("SunshadeDeparture", "close failed; no replay", error)
        CommandResult(1, "Sunshade closure not confirmed")
    } catch (error: LinkageError) {
        AppLog.error("SunshadeDeparture", "OEM SDK unavailable", error)
        CommandResult(1, "Sunshade SDK unavailable")
    }

    /** Same SDK, field and planner as Raha; no UI, motor override, or retry on failure. */
    fun openSunshadeOnReady(): CommandResult = try {
        check(BridgeSubscriptionSession.vipActive) { "VIP subscription inactive" }
        val sdk = OemSession()
        val plan = RahaVehiclePlan(sdk).create(RahaAction.SET_SUNSHADE_POSITION, mapOf("position" to 100))
        check(plan == listOf(RahaWrite("vehicle", "ROLL_OPEN_PERCENT", 100))) { "Unexpected shade plan" }
        plan.forEach(sdk::validate)
        val before = sdk.vehicle("ROLL_OPEN_PERCENT")
        check(before in 0..100) { "Sunshade position unavailable" }
        check(BridgeSubscriptionSession.vipActive && sdk.acc() == 2 && sdk.vehicle("HUM_VCU_READY") == 1 && sdk.speed() == 0) {
            "READY opening requires VIP, powered READY and stationary vehicle"
        }
        if (before != 100) {
            AppLog.info("SunshadeReady", "OEM request ROLL_OPEN_PERCENT=100 before=$before")
            sdk.write(plan.single())
            val deadline = android.os.SystemClock.elapsedRealtime() + 6000
            var actual = sdk.vehicle("ROLL_OPEN_PERCENT")
            while (actual != 100 && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(150)
                actual = sdk.vehicle("ROLL_OPEN_PERCENT")
            }
            check(actual == 100) { "Sunshade opening not confirmed: $actual" }
        }
        CommandResult(0, "SUNSHADE_OPENED")
    } catch (error: Exception) {
        AppLog.error("SunshadeReady", "open failed; no replay", error)
        CommandResult(1, "Sunshade opening not confirmed")
    } catch (error: LinkageError) {
        AppLog.error("SunshadeReady", "OEM SDK unavailable", error)
        CommandResult(1, "Sunshade SDK unavailable")
    }

    fun execute(actionName: String, json: String): CommandResult { return try {
        val action = RahaAction.valueOf(actionName)
        val data = JSONObject(json)
        val params = data.keys().asSequence().associateWith { key ->
            val value = data.get(key)
            require(value is String || value is Number || value is Boolean) { "Invalid parameter $key" }
            value
        }
        if (action == RahaAction.SWAP_DISPLAY_APPS) {
            require(params.isEmpty())
            val result = OemScreenSwapManager.command("execute")
            return if (result.exitCode == 0 && result.output.trim() != "DONE")
                CommandResult(1, "Screen transfer unavailable: ${result.output}") else result
        }
        if (action.name in OemEnergyBinder.actions) {
            OemEnergyBinder.execute(action.name, params)
            return CommandResult(0, "CONFIRMED action=$action via direct OEM Binder feedback")
        }
        if (action.name in OemDriverAssistanceBinder.actions) {
            OemDriverAssistanceBinder.execute(action.name, params)
            return CommandResult(0, "CONFIRMED action=$action via direct OEM Binder feedback")
        }
        if (action in OemPhaseSevenActions.actions) {
            OemPhaseSevenActions.execute(action, params)
            return CommandResult(0, "DISPATCHED action=$action via direct OEM services")
        }
        if (action.name in OemSceneActions.actions) {
            return CommandResult(0, OemSceneActions.execute(action.name, params))
        }
        if (action in OemEnergySceneActions.actions) {
            OemEnergySceneActions.execute(action, params)
            return CommandResult(0, "DISPATCHED action=$action through installed OEM Settings; ECU completion is reported by vehicle")
        }
        val sdk = OemSession()
        val plan = RahaVehiclePlan(sdk).create(action, params)
        // Resolve every enum and Binder method before any physical action.
        plan.forEach(sdk::validate)
        val windows = plan.filter { it.family == "window-position" }
        if (windows.isNotEmpty()) {
            WindowPositionController(sdk::vehicle,
                { field, value -> sdk.write(RahaWrite("vehicle", field, value)) },
                { AppLog.info("RahaWindow", "action=$action $it") }).move(windows)
        }
        for ((index, write) in plan.filter { it.family != "window-position" }.withIndex()) {
            AppLog.info("RahaVehicle", "action=$action step=${index + 1}/${plan.size} write=$write")
            sdk.write(write)
            if (index < plan.lastIndex) Thread.sleep(150)
        }
        if (action == RahaAction.SET_DRIVE_MODE) {
            // Wait for the selected mode before presenting the OEM's current selection.
            val requested=plan.firstOrNull { it.family == "drive" }?.value
            try {
                val deadline=android.os.SystemClock.elapsedRealtime()+2000
                while(requested != null && sdk.vehicle("DRIVING_MODE_SET") != requested && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
                check(requested == null || sdk.vehicle("DRIVING_MODE_SET")==requested) { "Mode feedback not ready for popup" }
                OemDriveModePopup.show()
            } catch(e:Exception) { AppLog.error("RahaOemUi", "Popup failed; drive command will not be replayed",e) }
        }
        CommandResult(0, "DISPATCHED action=$action operations=${plan.size}; ECU completion is reported by vehicle")
    } catch (e: Exception) {
        AppLog.error("RahaVehicle", "action=$actionName failed; not replayed", e)
        CommandResult(1, "${e.cause ?: e}")
    }

    }

    internal class OemSession : RahaVehicleSnapshot {
        private val apk = listOf(
            "/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk",
            "/system/priv-app/VehicleAir-release-signed/VehicleAir-release-signed.apk"
        ).map(::File).firstOrNull(File::isFile) ?: error("Installed OEM vehicle SDK unavailable")
        private val loader = object : PathClassLoader(apk.path, RahaVehicleBridge::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.canbus.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        private val vehicleType = loader.loadClass("com.qinggan.canbus.VehicleState")
        private val airType = loader.loadClass("com.qinggan.canbus.AirConditionState")
        private val api = loader.loadClass("com.qinggan.canbus.ICanBusService")
        private val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.canbus") as? IBinder ?: error("CAN service unavailable")
        private val remote = loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        init { check(binder.isBinderAlive && binder.interfaceDescriptor == api.name) { "CAN descriptor mismatch" } }
        private fun field(type: Class<*>, name: String): Any {
            // The OEM facade has aliases (e.g. LOW_BEAM_SWITCH -> VehicleState.LOW_BEAM).
            val member = try { type.getField(name) } catch (e: NoSuchFieldException) {
                if (type != vehicleType) throw e
                loader.loadClass("com.qinggan.canbus.DFVehicleState").getField(name)
            }
            return checkNotNull(member.get(null))
        }
        fun stateIdentity(name: String): Pair<Int, Int> {
            val value = field(vehicleType, name)
            return (value as Enum<*>).ordinal to (vehicleType.getMethod("getValue").invoke(value) as Int)
        }
        fun watch(callback: android.os.IBinder, register: Boolean) {
            val data = android.os.Parcel.obtain(); val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(api.name); data.writeStrongBinder(callback)
                check(binder.transact(if (register) 0x1c else 0x1d, data, reply, 0))
                reply.readException()
                if (register) check(reply.readInt() != 0) { "CAN callback rejected" }
            } finally { data.recycle(); reply.recycle() }
        }
        fun writeAmbient(suffix: String, value: Int) {
            val range = when(suffix) {
                "SWITCH", "MUSICAL_RHYTHM_SWITCH" -> 1..2
                "COLOR" -> 1..64
                "BRIGHTNESS_LEVEL" -> 1..10
                else -> error("Unsupported ambient field")
            }
            require(value in range)
            val result = api.getMethod("setVehicleState", vehicleType, Int::class.javaPrimitiveType)
                .invoke(remote, field(vehicleType, "VEHICLE_AMBIENT_LIGHT_" + suffix), value)
            check(result !is Number || result.toInt() >= 0) { "Ambient command rejected" }
        }
        fun writeAmbientBundle(values: Map<String, Int>) {
            val bundle = DowAmbientBundle.create(values) {
                field(vehicleType, "VEHICLE_AMBIENT_LIGHT_" + it).toString()
            }
            val result = api.getMethod("setVehicleAndAirConditionBundleState",
                android.os.Bundle::class.java, android.os.Bundle::class.java).invoke(remote, null, bundle)
            check(result !is Number || result.toInt() >= 0) { "Ambient bundle rejected" }
        }
        fun driveAmbient(): Int {
            val result = ProcessCommandRunner(useBridge = false).run(listOf("/system/bin/content", "query",
                "--uri", "content://qinggan.settings/global", "--user", "0", "--projection", "value",
                "--where", "name='vehicle_setting_drive_mode_atm'"), 2)
            check(result.exitCode == 0 && !result.timedOut && !result.output.contains("Error", true))
            // Factory getInt default is 0 when no row exists.
            if (result.output.contains("No result found")) return 0
            return Regex("value=([01])(?:\\s|$)").find(result.output)?.groupValues?.get(1)?.toInt()
                ?: error("Ambient drive-mode preference unavailable")
        }
        fun acc() = api.getMethod("getAccStatus").invoke(remote) as Int
        fun driverDoor(): Int {
            val doors = api.getMethod("getDoorStatus").invoke(remote) ?: return -1
            return doors.javaClass.getMethod("getFLDoor").invoke(doors) as Int
        }
        override fun speed() = api.getMethod("getVehicleSpeed").invoke(remote) as Int
        override fun vehicle(field: String) = api.getMethod("getVehicleState", vehicleType).invoke(remote, field(vehicleType, field)) as Int
        override fun air(field: String) = api.getMethod("getAirConditionState", airType).invoke(remote, field(airType, field)) as Int
        override fun airValue(getter: String): Double {
            val value = api.getMethod("getAirCondition").invoke(remote) ?: error("Air status unavailable")
            return (value.javaClass.getMethod(getter).invoke(value) as Number).toDouble()
        }
        override fun light(getter: String): Int {
            val value = api.getMethod("getLightStatus").invoke(remote) ?: error("Light status unavailable")
            return value.javaClass.getMethod(getter).invoke(value) as Int
        }
        override fun overseas() = loader.loadClass("com.qinggan.utils.AppCommonUtils")
            .getMethod("isOverSeaVehicle").invoke(null) as Boolean
        fun validate(write: RahaWrite) {
            if (write.family == "air-state") {
                val getter = ClimateStateController.feedback.getValue(write.field)
                require(write.value in if (write.field == "AC_RECIRC_AIR") 0..2 else 0..1)
                field(airType, write.field)
                api.getMethod("setAirConditionState", airType, Int::class.javaPrimitiveType)
                api.getMethod("getAirCondition").returnType.getMethod(getter)
                return
            }
            if (write.family == "window-position") {
                require(write.value in 0..100)
                field(vehicleType, WindowPositionController.feedback.getValue(write.field))
                api.getMethod("getVehicleState", vehicleType)
            }
            if (write.family == "drive") { require(write.value in 1..6); return }
            if (write.family == "qg") {
                require(write.field == "vehicle_setting_drive_mode_atm" && write.value in 0..1)
                return
            }
            val type = if (write.family == "air") airType else vehicleType
            field(type, write.field)
            api.getMethod(if (write.family == "air") "setAirConditionState" else "setVehicleState", type, Int::class.javaPrimitiveType)
        }
        fun write(write: RahaWrite) {
            if (write.family == "air-state") {
                ClimateStateController(
                    { airValue(it).toInt() },
                    { field -> write(RahaWrite("air", field, 1)) },
                    { AppLog.info("RahaVehicle", "climate $it") }
                ).set(write.field, write.value)
                return
            }
            if (write.family == "qg") {
                val runner = ProcessCommandRunner(useBridge = false)
                val base = listOf("/system/bin/content")
                val insert = runner.run(base + listOf("insert", "--uri", "content://qinggan.settings/global", "--user", "0",
                    "--bind", "name:s:${write.field}", "--bind", "value:s:${write.value}"), 5)
                check(insert.exitCode == 0 && !insert.output.contains("Error", true)) { insert.output }
                val query = runner.run(base + listOf("query", "--uri", "content://qinggan.settings/global", "--user", "0",
                    "--projection", "value", "--where", "name='${write.field}'"), 5)
                check(query.exitCode == 0 && Regex("value=${write.value}(?:\\s|$)").containsMatchIn(query.output)) { "OEM ambient preference not confirmed: ${query.output}" }
                return
            }
            if (write.family == "drive") {
                val result = ProcessCommandRunner(useBridge = false).run(listOf("/system/bin/am") + OemDriveModeWriter.arguments(write.value).drop(1), 8)
                check(result.exitCode == 0 && !result.output.contains("Error", ignoreCase = true)) { result.output }
                return
            }
            val type = if (write.family == "air") airType else vehicleType
            val observedField = when(write.field) {
                "ESC_SWITCH" -> "ESC_SWITCH"
                "EPB_PARK_SWITCH" -> "EPB_PARK_STATUS"
                else -> null
            }
            val desiredFeedback = if(write.field == "EPB_PARK_SWITCH") {
                if(write.value == 1) 1 else 0
            } else write.value
            val before = observedField?.let { vehicle(it) }
            if(observedField != null) {
                AppLog.info("RahaVehicle", "request=${write.field} value=${write.value} feedbackField=$observedField before=$before target=$desiredFeedback speed=${speed()} brake=${vehicle("BRAKE_PEDAL_STATUS")}")
                if(before == desiredFeedback) return
            }
            api.getMethod(if (write.family == "air") "setAirConditionState" else "setVehicleState", type, Int::class.javaPrimitiveType)
                .invoke(remote, field(type, write.field), write.value)
            if(observedField != null) {
                val deadline=android.os.SystemClock.elapsedRealtime()+3000
                var actual: Int
                do {
                    Thread.sleep(100)
                    actual=vehicle(observedField)
                    if(actual == desiredFeedback) break
                } while(android.os.SystemClock.elapsedRealtime()<deadline)
                AppLog.info("RahaVehicle", "request=${write.field} before=$before target=$desiredFeedback actual=$actual")
                check(actual == desiredFeedback) { "Vehicle did not confirm ${write.field}: before=$before target=$desiredFeedback actual=$actual" }
            }
        }
    }
}
