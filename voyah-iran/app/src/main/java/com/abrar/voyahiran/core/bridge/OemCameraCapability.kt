package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import java.io.File

internal object OemCameraCapability {
    /** Same option check as Camera SupportUtils.isSupportDvr(); never enables the option. */
    fun requireDvr() {
        val apk = File("/system/priv-app/Setting-release-signed/Setting-release-signed.apk")
        check(apk.isFile) { "DVR configuration SDK unavailable" }
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
        val value = try {
            val states = loader.loadClass("com.qinggan.vehicle.VehicleOnlineState")
            loader.loadClass("com.qinggan.vehicle.VehicleConfigHelper")
                .getMethod("readOnlineConfigItem", states).invoke(null, states.getField("DVR").get(null)) as Int
        } catch (e: LinkageError) { throw IllegalStateException("DVR configuration unavailable", e) }
        AppLog.info("RahaCamera", "DVR option=$value")
        check(value == 1) { "آپشن DVR خودرو فعال نیست یا قابل بررسی نیست" }
    }
}
