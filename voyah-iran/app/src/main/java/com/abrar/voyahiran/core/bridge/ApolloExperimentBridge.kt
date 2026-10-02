package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File

/** Symbolic fields from the installed SDK: no firmware-specific ordinal guessing. */
internal object ApolloExperimentBridge {
    fun execute(name: String? = null, value: Int? = null): CommandResult = try {
        val setting = name?.let { ApolloExperiment.valueOf(it) }
        if (setting != null) {
            check(BridgeSubscriptionSession.active) { "Subscription inactive" }
            require(value != null && setting.accepts(value))
        }
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Vehicle SDK unavailable" }
        val loader = object : PathClassLoader(apk.path, "/system/lib64:/system/lib", javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.canbus.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        val facade = loader.loadClass("com.qinggan.canbus.DFVehicleState")
        val type = loader.loadClass("com.qinggan.canbus.VehicleState")
        val api = loader.loadClass("com.qinggan.canbus.ICanBusService")
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.canbus") as? IBinder ?: error("CAN unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor == api.name)
        val remote = loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val getter = api.getMethod("getVehicleState", type)
        fun state(s: ApolloExperiment) = checkNotNull(facade.getField(s.field).get(null))
        if (setting == null) {
            val json = JSONObject()
            ApolloExperiment.entries.forEach { s ->
                try {
                    val actual = (getter.invoke(remote, state(s)) as Number).toInt()
                    json.put(s.name, actual)
                    AppLog.changed("ApolloTest/${s.field}", "read=$actual")
                } catch (e: Exception) {
                    json.put(s.name, JSONObject.NULL)
                    AppLog.error("ApolloTest", "Read unavailable: ${s.field}", e)
                }
            }
            CommandResult(0, json.toString())
        } else {
            val key = state(setting)
            val before = (getter.invoke(remote, key) as Number).toInt()
            check(setting.accepts(before)) { "Unsupported current state: $before" }
            val result = api.getMethod("setVehicleState", type, Int::class.javaPrimitiveType).invoke(remote, key, value)
            check(result !is Number || result.toInt() >= 0) { "OEM rejected: $result" }
            val deadline = SystemClock.elapsedRealtime() + 2000
            var actual = (getter.invoke(remote, key) as Number).toInt()
            while (actual != value && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100)
                actual = (getter.invoke(remote, key) as Number).toInt()
            }
            AppLog.info("ApolloTest", "field=${setting.field} before=$before requested=$value actual=$actual result=$result")
            check(actual == value) { "Vehicle feedback mismatch: expected=$value actual=$actual" }
            CommandResult(0, "${setting.field}=$actual")
        }
    } catch (e: Exception) {
        AppLog.error("ApolloTest", "Request failed", e)
        CommandResult(1, "Apollo test failed: ${e.message}")
    }
}
