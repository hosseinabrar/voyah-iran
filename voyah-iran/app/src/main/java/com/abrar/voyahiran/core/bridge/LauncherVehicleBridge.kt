package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter
import com.abrar.voyahiran.feature.voice.EnergyStrategyPolicy
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File

/** Bounded, symbolic operations; the factory drive service retains its ECU interlocks and mode bundle. */
internal object LauncherVehicleBridge {
    private var cached: Session? = null
    @Synchronized fun execute(op: String, value: String = ""): CommandResult = try {
        val s=cached?.takeIf { it.binder.isBinderAlive } ?: Session().also { cached=it }
        if(op == "read") CommandResult(0,s.readAll().toString()) else {
            check(BridgeSubscriptionSession.active) { "Subscription inactive" }
            when(op) {
                "energy" -> OemEnergyBinder.execute("SET_ENERGY_STRATEGY",mapOf("strategy" to value))
                "recovery" -> {
                    check(s.read("HUM_ENERGY_PTREGEN_LEVL_FB") in 2..4) { "Recovery unavailable" }
                    s.write("HUM_ENERGY_PTREGEN_LEVL",value.toInt().also { require(it in 2..4) })
                }
                "height", "drive" -> {
                    check(s.read("DRIVING_MODE_SET") in 1..6) { "Driving mode unavailable" }
                    if(op == "height") {
                        check(s.flag("isSupportAsc")) { "Air suspension not equipped" }
                        check(s.read("ASC_MODE_SELECT") in 1..9 && s.read("ASC_MAINTAIN_SWITCH") in 0..1 && s.read("ASC_ADJUST_SUSTEMP") in listOf(0,2)) { "Suspension adjustment unavailable" }
                    } else check(s.read("ASC_MAINTAIN_SWITCH") != 2) { "Suspension maintenance active" }
                    if(op == "height" && value == "0") {
                        check(s.read("MANUAL_EASY_ENTER_SET") in 1..2) { "Easy entry unavailable" }
                        s.write("MANUAL_EASY_ENTER_SET",2)
                    } else {
                        val mode=if(op == "drive") value.toInt().also { require(it in 1..6) } else when(value) {
                            "1" -> 3; "2" -> s.read("DRIVING_MODE_SET").takeIf { it in listOf(1,2,5,6) } ?: 2
                            "3" -> 4; else -> error("Invalid height")
                        }
                        check(mode != 4 || s.speed() in 0..39) { "Outing requires speed below 40 km/h" }
                        check(mode != 4 || s.flag("isSupportOffload")) { "Outing not supported" }
                        val r=ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/am")+OemDriveModeWriter.arguments(mode).drop(1),8)
                        check(r.exitCode==0 && !r.timedOut && !r.output.contains("Error",true)) { r.output }
                    }
                }
                else -> error("Unknown launcher operation")
            }
            AppLog.info("LauncherVehicle","requested $op=$value; awaiting vehicle feedback")
            CommandResult(0,"DISPATCHED")
        }
    } catch(e:Exception) {
        cached=null
        AppLog.error("LauncherVehicle","$op failed",e)
        CommandResult(1,(e.cause ?: e).toString())
    }
    private class Session {
        private val apk=File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        private val loader=object:PathClassLoader(apk.path,"/system/lib64:/system/lib",javaClass.classLoader) {
            override fun loadClass(name:String,resolve:Boolean):Class<*> {
                if(!name.startsWith("com.qinggan.canbus.") && !name.startsWith("com.qinggan.vehicle.")) return super.loadClass(name,resolve)
                synchronized(this) { val c=findLoadedClass(name) ?: findClass(name);if(resolve)resolveClass(c);return c }
            }
        }
        private val type=loader.loadClass("com.qinggan.canbus.VehicleState")
        private val facade=loader.loadClass("com.qinggan.canbus.DFVehicleState")
        private val api=loader.loadClass("com.qinggan.canbus.ICanBusService")
        val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java).invoke(null,"qg.canbus") as IBinder
        private val remote=loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder)
        private val get=api.getMethod("getVehicleState",type)
        private val fields=HashMap<String,Any>()
        private val configType by lazy {loader.loadClass("com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs")}
        private val config by lazy {configType.getConstructor().newInstance()}
        fun flag(name:String)=configType.getMethod(name).invoke(config) as Boolean
        init { check(apk.isFile && binder.interfaceDescriptor==api.name) }
        private fun field(name:String)=fields.getOrPut(name) { (try {facade.getField(name)} catch(_:NoSuchFieldException){type.getField(name)}).get(null)!! }
        fun read(name:String)=(get.invoke(remote,field(name)) as Number).toInt()
        fun speed()=(api.getMethod("getVehicleSpeed").invoke(remote) as Number).toInt()
        fun write(name:String,value:Int) {
            val result=api.getMethod("setVehicleState",type,Int::class.javaPrimitiveType).invoke(remote,field(name),value)
            check(result !is Number || result.toInt()>=0) { "Vehicle rejected $name" }
        }
        private val modes by lazy {
            runCatching {
                val t=configType
                if(t.methods.none { it.name=="isDoFengH97acd" }) EnergyStrategyPolicy.legacyModes(flag("isSupportExtends"))
                else EnergyStrategyPolicy.modes(flag("isSupportExtends"),flag("isDoFengH97acd"),
                    if(t.methods.any {it.name=="is97XOversea"}) flag("is97XOversea") else false)
            }.getOrElse { AppLog.error("LauncherVehicle","Energy capabilities unavailable",it);emptyMap() }
        }
        fun readAll()=JSONObject().apply {
            for(n in listOf("DRIVING_MODE_SET","ASC_MODE_SELECT","ASC_ADJUST_DIREACT","ASC_MAINTAIN_SWITCH","ASC_ADJUST_SUSTEMP",
                "HUM_ENERGY_PTREGEN_LEVL_FB","IVI_SOC_MODESET","AMBIENT_AIR_PRESSURE","ASC_SENSOR_ERROR_STS",
                "ASC_RF_HEIGHT","ASC_LF_HEIGHT","ASC_LR_HEIGHT","ASC_RR_HEIGHT","MANUAL_EASY_ENTER_SET"))
                put(n,runCatching { read(n) }.getOrNull() ?: JSONObject.NULL)
            put("speed",runCatching {speed()}.getOrNull() ?: JSONObject.NULL)
            put("modes",JSONObject(modes))
            put("supportsSuspension",runCatching{flag("isSupportAsc")}.getOrDefault(false))
            put("supportsOuting",runCatching{flag("isSupportOffload")}.getOrDefault(false))
        }
    }
}
