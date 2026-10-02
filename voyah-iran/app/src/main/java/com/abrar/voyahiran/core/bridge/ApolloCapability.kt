package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.io.File

/** Read the equipment flag used by OEM DriveAssistantConfig.isSupportSDB(). */
internal object ApolloCapability {
    fun read(): CommandResult = try {
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Vehicle configuration SDK unavailable" }
        val loader = object : PathClassLoader(apk.path, "/system/lib64:/system/lib", javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.vehicle.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        val states = loader.loadClass("com.qinggan.vehicle.VehicleOnlineState")
        val sdb = (loader.loadClass("com.qinggan.vehicle.VehicleConfigHelper")
            .getMethod("readOnlineConfigItem", states)
            .invoke(null, states.getField("SDB").get(null)) as Number).toInt()
        AppLog.info("Apollo", "equipment SDB=$sdb menuVisible=${sdb == 1}")
        CommandResult(if (sdb >= 0) 0 else 1, "APOLLO_CAPABILITY sdb=$sdb")
    } catch (e: Exception) {
        AppLog.error("Apollo", "equipment read failed", e)
        CommandResult(1, "APOLLO_CAPABILITY unknown")
    } catch (e: LinkageError) {
        AppLog.error("Apollo", "equipment SDK unavailable", e)
        CommandResult(1, "APOLLO_CAPABILITY unknown")
    }
}
