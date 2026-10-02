package com.abrar.voyahiran.core.bridge

import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.voice.EnergyCommandExecutor
import com.abrar.voyahiran.feature.voice.EnergyCommandPort
import com.abrar.voyahiran.feature.voice.EnergyStrategyPolicy
import dalvik.system.PathClassLoader
import java.io.File

/** Read installed menu capabilities, then use the same Binder bundle as setPowerMode(). */
internal object OemEnergyBinder {
    val actions=setOf("SET_ENERGY_STRATEGY","SET_BATTERY_RESERVE_TARGET","SET_BATTERY_PRECONDITIONING")
    fun execute(action: String, parameters: Map<String,Any>) {
        try { executeInstalled(action,parameters) }
        catch(error:LinkageError) {throw IllegalStateException("Installed energy SDK could not be loaded",error)}
    }
    private fun executeInstalled(action: String, parameters: Map<String,Any>) {
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
        val menuType=loader.loadClass("com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs")
        val legacyMenu=menuType.methods.none { it.name == "isDoFengH97acd" && it.parameterCount == 0 }
        val port=object : EnergyCommandPort {
            override fun modes():Map<String,Int> {
                val type=loader.loadClass("com.qinggan.app.vehiclesetting.fragments.drivepreference.DrivePreferenceConfigs")
                val config=type.getConstructor().newInstance()
                fun flag(name:String)=type.getMethod(name).invoke(config) as Boolean
                val extender=flag("isSupportExtends")
                if (legacyMenu) {
                    // Supplied H97A menu has four fixed labels, no hybrid relabelling.
                    check(type.methods.any { it.name == "isDoFengH97c" && it.parameterCount == 0 }) { "Unknown energy menu capability contract" }
                    return EnergyStrategyPolicy.legacyModes(extender).also {
                        AppLog.info("RahaEnergy", "route=binder profile=H97A-legacy extender=$extender modes=$it")
                    }
                }
                val ca=flag("isDoFengH97acd")
                // Old China firmware does not expose this method. Other failures are not swallowed.
                val xMethod=try {type.getMethod("is97XOversea")} catch(_:NoSuchMethodException){null}
                val overseas=xMethod?.invoke(config) as? Boolean ?: false
                return EnergyStrategyPolicy.modes(extender,ca,overseas).also {
                    AppLog.info("RahaEnergy","route=binder sdk=${apk.path} extender=$extender ca=$ca xOverseas=$overseas modes=$it")
                }
            }
            override fun read(field:String)=api.getMethod("getVehicleState",stateType).invoke(remote,field(field)) as Int
            override fun validate(field:String,bundle:Boolean) {
                field(field)
                check(!legacyMenu || field != "SREV_SOC_SET") { "This factory energy menu cannot set a reserve percentage" }
                if(bundle && !legacyMenu)api.getMethod("setVehicleAndAirConditionBundleState",Bundle::class.java,Bundle::class.java)
                else api.getMethod("setVehicleState",stateType,Int::class.javaPrimitiveType)
            }
            override fun write(field:String,value:Int,bundle:Boolean) {
                AppLog.info("RahaEnergy","write field=$field value=$value bundle=$bundle")
                val result=if(bundle && !legacyMenu) {
                    val values=Bundle().apply {putInt(field(field).toString(),value)}
                    api.getMethod("setVehicleAndAirConditionBundleState",Bundle::class.java,Bundle::class.java).invoke(remote,null,values)
                } else api.getMethod("setVehicleState",stateType,Int::class.javaPrimitiveType).invoke(remote,field(field),value)
                AppLog.info("RahaEnergy","Binder returned=$result field=$field")
                check(result !is Number || result.toInt()>=0) { "OEM rejected $field: $result" }
            }
            override fun confirm(field:String,value:Int) {
                val deadline=SystemClock.elapsedRealtime()+3000
                var actual=read(field)
                while(actual!=value && SystemClock.elapsedRealtime()<deadline) {Thread.sleep(100);actual=read(field)}
                AppLog.info("RahaEnergy","feedback field=$field expected=$value actual=$actual")
                check(actual==value) { "Energy feedback not confirmed: $field expected=$value actual=$actual" }
            }
        }
        AppLog.info("RahaEnergy","direct Binder action=$action; OEM process injection not used")
        if (action == "SET_BATTERY_PRECONDITIONING") {
            require(parameters.keys == setOf("enabled") && parameters["enabled"] is Boolean)
            val key = "DRIVER_PREHEAT_SET"
            val desired = if (parameters["enabled"] == true) 1 else 0
            port.validate(key, false)
            val current = port.read(key)
            check(current in 0..1) { "Battery temperature control unavailable: $current" }
            if (current != desired) port.write(key, desired, false)
            port.confirm(key, desired)
            return
        }
        EnergyCommandExecutor(port).execute(action,parameters)
    }
}
