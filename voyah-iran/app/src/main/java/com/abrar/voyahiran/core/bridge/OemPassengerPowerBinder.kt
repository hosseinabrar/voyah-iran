package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import com.abrar.voyahiran.feature.voice.PassengerPowerController
import com.abrar.voyahiran.feature.voice.PassengerPowerPort
import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import java.io.File

/** Calls the factory power state machine; never writes a property or just dims the panel. */
internal object OemPassengerPowerBinder {
    fun execute(parameters: Map<String, Any>) {
        try { executeInstalled(parameters) }
        catch (error: LinkageError) { throw IllegalStateException("Installed OEM power SDK unavailable", error) }
    }
    private fun executeInstalled(parameters: Map<String, Any>) {
        require(parameters.keys == setOf("enabled") && parameters["enabled"] is Boolean)
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Installed OEM SDK unavailable" }
        val loader = PathClassLoader(apk.path, javaClass.classLoader)
        val api = loader.loadClass("com.qinggan.system.IPowerManagerService")
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.power_manager") as? IBinder ?: error("OEM power service unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor == api.name) { "OEM power descriptor mismatch" }
        val remote = loader.loadClass("com.qinggan.system.IPowerManagerService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val intType = Int::class.javaPrimitiveType
        val read = api.getMethod("getAVNStatusForScreen", intType)
        val off = api.getMethod("enterScreenOffForScreen", intType)
        val on = api.getMethod("exitScreenOffForScreen", intType)
        PassengerPowerController(object : PassengerPowerPort {
            override fun state(screen: Int) = read.invoke(remote, screen) as Int
            override fun setOff(screen: Int, disabled: Boolean) {
                (if (disabled) off else on).invoke(remote, screen)
                AppLog.info("RahaPassengerPower", "requested screen=$screen off=$disabled")
            }
            override fun pause(milliseconds: Long) { Thread.sleep(milliseconds) }
        }).execute(parameters.getValue("enabled") as Boolean)
        AppLog.info("RahaPassengerPower", "confirmed enabled=${parameters["enabled"]}")
    }
}
