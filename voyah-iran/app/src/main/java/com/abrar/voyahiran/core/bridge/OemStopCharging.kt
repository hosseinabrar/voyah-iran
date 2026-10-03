package com.abrar.voyahiran.core.bridge

import android.os.Bundle
import android.os.IBinder
import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import java.io.File

/** Same bundle as VehicleControl.stopChargeNow; independent of the Settings process/UI. */
internal object OemStopCharging {
    private val loader by lazy {
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Vehicle SDK unavailable" }
        object : PathClassLoader(apk.path, javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.canbus.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
    }
    fun execute() {
        val api = loader.loadClass("com.qinggan.canbus.ICanBusService")
        val field = checkNotNull(loader.loadClass("com.qinggan.canbus.DFVehicleState")
            .getField("HUM_STOP_CHARGE").get(null))
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.canbus") as? IBinder ?: error("CAN service unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor == api.name) { "CAN descriptor mismatch" }
        val remote = loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val method = api.getMethod("setVehicleAndAirConditionBundleState", Bundle::class.java, Bundle::class.java)
        val values = Bundle().apply { putInt(field.toString(), 1) }
        AppLog.info("RahaCharging", "stop route=binder field=$field value=1")
        val result = method.invoke(remote, Bundle(), values)
        AppLog.info("RahaCharging", "stop dispatch result=$result (not ECU completion feedback)")
        check(result is Number && result.toInt() >= 0) { "Stop charging rejected: $result" }
        // HUM_STOP_CHARGE is a transient command, not a persistent charging status.
        // Do not poll its cache for 1 or replay the request when feedback is unavailable.
    }
}
