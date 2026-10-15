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
    internal fun setLauncherFan(level:Int):CommandResult = try {
        require(level in 0..7);check(BridgeSubscriptionSession.vipActive)
        val sdk=OemSession()
        val writes=listOf(RahaWrite("air-state","AC_POWER_SWITCH",1),RahaWrite("air","AC_BLOWER",level+1))
        writes.forEach(sdk::validate);writes.forEach(sdk::write)
        CommandResult(0,"DISPATCHED")
    } catch(e:Exception){CommandResult(1,(e.cause ?: e).toString())}
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

    private class OemSession : RahaVehicleSnapshot {
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
