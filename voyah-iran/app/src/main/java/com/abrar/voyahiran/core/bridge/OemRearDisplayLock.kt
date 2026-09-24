package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import dalvik.system.PathClassLoader
import java.io.File

/** Uses DrivePreferenceFragment's provider setting, without attaching to its process. */
internal object OemRearDisplayLock {
    fun execute(enabled: Boolean) {
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Installed vehicle SDK unavailable" }
        val loader = object : PathClassLoader(apk.path, "/system/lib64:/system/lib", javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        val supported = try {
            loader.loadClass("com.qinggan.utils.AppCommonUtils").getMethod("is97CBasePlatform").invoke(null) as Boolean
        } catch (e: LinkageError) { throw IllegalStateException("Rear display platform check unavailable", e) }
        check(supported) { "Rear-screen lock unavailable on this platform" }
        val value = if (enabled) 2 else 0
        val key = "MainScreenVehicleRearSCRLock"
        val runner = ProcessCommandRunner(useBridge = false)
        fun run(arguments: List<String>): String {
            val result = runner.run(listOf("/system/bin/content") + arguments, 5)
            check(result.exitCode == 0 && !result.timedOut &&
                !Regex("Error|Exception|Permission Denial", RegexOption.IGNORE_CASE).containsMatchIn(result.output)) { result.output }
            return result.output
        }
        val uri = "content://qinggan.settings/global"
        run(listOf("insert", "--uri", uri, "--user", "0", "--bind", "name:s:$key", "--bind", "value:s:$value"))
        val output = run(listOf("query", "--uri", uri, "--user", "0", "--projection", "value", "--where", "name='$key'"))
        val confirmed = Regex("(?:^|[ ,])value=$value(?:\\s|$)").containsMatchIn(output)
        AppLog.info("RahaDisplay", "rear lock route=provider enabled=$enabled expected=$value confirmed=$confirmed feedback=$output")
        check(confirmed) { "Rear-screen lock setting not confirmed: $output" }
    }
}
