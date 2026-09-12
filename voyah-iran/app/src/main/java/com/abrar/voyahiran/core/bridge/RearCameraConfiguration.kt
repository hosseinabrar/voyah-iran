package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.io.File

/** Uses the installed OEM settings SDK, exactly as VehicleInfoSettingActivity does. */
object RearCameraConfiguration {
    fun enable(): CommandResult = try {
        val apk = File("/system/priv-app/Setting-release-signed/Setting-release-signed.apk")
        check(apk.isFile) { "OEM Settings APK unavailable" }
        // Fresh loader prevents the OEM helper's static configuration cache surviving a request.
        val loader = object : PathClassLoader(apk.path, javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.vehicle.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        val helper = loader.loadClass("com.qinggan.vehicle.VehicleConfigHelper")
        val states = loader.loadClass("com.qinggan.vehicle.VehicleOnlineState")
        val rpm = states.getField("RPM").get(null)
        val before = helper.getMethod("readOnlineConfigItem", states).invoke(null, rpm) as Int
        check(before in 0..1) { "Invalid RPM configuration: $before" }
        if (before == 1) CommandResult(0, "RPM_CONFIG=ENABLED") else {
            val backup = helper.getMethod("getVehicleTypeSUB").invoke(null) as IntArray
            val folder = File("/data/local/tmp/voyahiran-bridge/config-backups").apply { mkdirs() }
            File(folder, "before-rpm-${System.currentTimeMillis()}.txt").writeText(backup.joinToString(","))
            // No Context overload: do not send OEM's automatic reboot broadcast.
            val result = helper.getMethod("writeOnlineConfigItem", states, Int::class.javaPrimitiveType)
                .invoke(null, rpm, 1) as Int
            AppLog.info("RearCameraConfig", "OEM RPM write result=$result previous=$before; reboot required")
            check(result == 0) { "OEM RPM configuration write failed: $result" }
            // OEM write success is not proof the running camera has reloaded its configuration.
            CommandResult(0, "RPM_CONFIG=REBOOT_REQUIRED")
        }
    } catch (error: Exception) {
        AppLog.error("RearCameraConfig", "OEM configuration failed; no retry", error)
        CommandResult(1, "RPM configuration not confirmed: ${error.cause ?: error}")
    }
}
