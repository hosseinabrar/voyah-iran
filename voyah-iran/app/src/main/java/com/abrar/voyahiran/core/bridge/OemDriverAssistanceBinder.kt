package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.voice.DriverAssistanceExecutor
import com.abrar.voyahiran.feature.voice.DriverAssistancePort
import dalvik.system.PathClassLoader
import java.io.File

/** Executes factory driver-assistance settings without starting an Activity or injecting an OEM process. */
internal object OemDriverAssistanceBinder {
    val actions=DriverAssistanceExecutor.actions
    fun execute(action: String, parameters: Map<String,Any>) {
        try { executeInstalled(action,parameters) }
        catch(error:LinkageError) {throw IllegalStateException("Installed driver-assistance SDK could not be loaded",error)}
    }
    fun restoreAeb(value: Int) {
        require(value in 1..2)
        try { executeInstalled("SET_AUTOMATIC_EMERGENCY_BRAKING", mapOf("enabled" to (value == 1)), memoryRestore = true) }
        catch (error: LinkageError) { throw IllegalStateException("Installed AEB SDK could not be loaded", error) }
    }
    fun restorePedestrian(value: Int) {
        require(value in 1..2)
        // Unlike AEB, the OEM pedestrian sound switch uses 2=on, 1=off.
        try { executeInstalled("SET_PEDESTRIAN_WARNING_SOUND", mapOf("enabled" to (value == 2)), memoryRestore = true) }
        catch (error: LinkageError) { throw IllegalStateException("Installed pedestrian SDK could not be loaded", error) }
    }
    private fun executeInstalled(action: String, parameters: Map<String,Any>, memoryRestore: Boolean = false) {
        val apk=File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Installed VehicleSetting SDK unavailable" }
        val loader=object : PathClassLoader(apk.path,"/system/lib64:/system/lib",javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if(!name.startsWith("com.qinggan.vehicle.") && !name.startsWith("com.qinggan.canbus.")) return super.loadClass(name,resolve)
                synchronized(this) {
                    val type=findLoadedClass(name) ?: findClass(name)
                    if(resolve)resolveClass(type)
                    return type
                }
            }
        }
        val stateType=loader.loadClass("com.qinggan.canbus.VehicleState")
        val facade=loader.loadClass("com.qinggan.canbus.DFVehicleState")
        val api=loader.loadClass("com.qinggan.canbus.ICanBusService")
        val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java).invoke(null,"qg.canbus") as? IBinder ?: error("CAN service unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor==api.name) { "CAN descriptor mismatch" }
        val remote=loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder)
        fun field(name:String):Any = checkNotNull(try {facade.getField(name).get(null)} catch(_:NoSuchFieldException){stateType.getField(name).get(null)})
        // Only consult the specific factory feature flags; no screen or Fragment is initialized.
        val capability = when(action) {
            // Seat courtesy is independent of air-suspension availability.
            "SET_EASY_ENTRY" -> "isSupportASC"
            "SET_DOOR_OPEN_WARNING" -> "isSupportDOW"
            "SET_REAR_CROSS_TRAFFIC_WARNING" -> "isSupportRCTA"
            "SET_AUTOMATIC_EMERGENCY_BRAKING" -> "isSupportAEB"
            "SET_BLIND_SPOT_MONITORING" -> "isSupportBSD"
            "SET_DRIVER_MONITORING" -> "isSupportDMS"
            "SET_DRIVER_GESTURES" -> "isSupportGUESTID"
            else -> null
        }
        if(capability != null) {
            val config=loader.loadClass("com.qinggan.app.basevehiclesetting.canbustools.VehicleConfig")
            check(config.getMethod(capability).invoke(null) == true) { "Factory feature unavailable: $capability" }
        }
        val port=object : DriverAssistancePort {
            override fun validate(field:String) {
                field(field)
                api.getMethod("getVehicleState",stateType)
                api.getMethod("setVehicleState",stateType,Int::class.javaPrimitiveType)
            }
            override fun read(field:String):Int {
                val value=api.getMethod("getVehicleState",stateType).invoke(remote,field(field)) as Int
                AppLog.info("RahaAdas","read field=$field value=$value")
                return value
            }
            override fun write(field:String,value:Int) {
                if (memoryRestore) {
                    // Recheck at the write boundary, not only at the earlier app-side CAN sample.
                    check(BridgeSubscriptionSession.vipActive) { "VIP subscription inactive" }
                    check(api.getMethod("getAccStatus").invoke(remote) == 2) { "Comfort memory requires a powered vehicle" }
                }
                AppLog.info("RahaAdas","write action=$action field=$field value=$value")
                val result=api.getMethod("setVehicleState",stateType,Int::class.javaPrimitiveType).invoke(remote,field(field),value)
                check(result !is Number || result.toInt()>=0) { "OEM rejected $field: $result" }
            }
            override fun confirm(field:String,value:Int) {
                val deadline=SystemClock.elapsedRealtime()+3000
                var actual=read(field)
                while(actual!=value && SystemClock.elapsedRealtime()<deadline) {Thread.sleep(100);actual=read(field)}
                AppLog.info("RahaAdas","feedback action=$action field=$field expected=$value actual=$actual")
                check(actual==value) { "Driver-assistance feedback not confirmed: $field expected=$value actual=$actual" }
            }
            override fun pause(milliseconds:Long) {Thread.sleep(milliseconds)}
        }
        AppLog.info("RahaAdas","route=binder action=$action parameters=$parameters")
        val legacyLane = android.os.Build.VERSION.SDK_INT == 28
        if (legacyLane && action == "SET_LANE_SUPPORT_MODE") {
            val config=loader.loadClass("com.qinggan.app.basevehiclesetting.canbustools.VehicleConfig")
            val flags=if(parameters["mode"] == "keeping") listOf("isSupportLKW") else listOf("isSupportLDW","isSupportLKW","isSupportLCA")
            check(flags.any { config.getMethod(it).invoke(null) == true }) { "Factory lane option unavailable" }
        }
        DriverAssistanceExecutor(port, legacyLane).execute(action,parameters)
        AppLog.info("RahaAdas","confirmed action=$action")
    }
}
