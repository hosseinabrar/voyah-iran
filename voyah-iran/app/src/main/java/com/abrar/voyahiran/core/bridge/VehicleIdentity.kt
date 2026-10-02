package com.abrar.voyahiran.core.bridge
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.io.File
import com.abrar.voyahiran.core.logging.AppLog
object VehicleIdentity {
    fun read(): CommandResult = try {
        val apk=File("/system/priv-app/Setting-release-signed/Setting-release-signed.apk")
        check(apk.isFile)
        val loader=PathClassLoader(apk.path,"/system/lib64:/system/lib",javaClass.classLoader)
        val type=loader.loadClass("com.qinggan.os.SystemProperties")
        // OEM getVinCode() reads NVRAM slot 21. Call the read directly to avoid its raw-VIN log.
        val vin=(type.getMethod("nvramRead",Int::class.javaPrimitiveType).invoke(null,21) as? String)?.trim()?.uppercase(java.util.Locale.ROOT)
        if (vin == null || !vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) {
            // Do not log VIN or native return contents; log only the failure stage.
            AppLog.info("VehicleIdentity", "NVRAM slot=21 invalid empty=${vin.isNullOrEmpty()}")
            CommandResult(1,"Vehicle identity unavailable: invalid NVRAM result")
        } else CommandResult(0,vin)
    } catch(e: Throwable) {
        val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
        AppLog.error("VehicleIdentity", "NVRAM read failed", cause)
        CommandResult(1,"Vehicle identity unavailable: ${cause.javaClass.simpleName}")
    }
}
