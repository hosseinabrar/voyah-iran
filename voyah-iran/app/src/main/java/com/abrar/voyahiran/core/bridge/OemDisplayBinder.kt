package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.SystemClock
import com.abrar.raha.RahaAction
import com.abrar.voyahiran.core.logging.AppLog
import dalvik.system.PathClassLoader
import java.io.File
import kotlin.math.roundToInt

/** Same system-policy calls as DisplayPresenter, without injecting into Settings. */
internal object OemDisplayBinder {
    fun execute(action: RahaAction, p: Map<String, Any>) {
        try { executeInstalled(action, p) }
        catch (e: LinkageError) { throw IllegalStateException("Installed display SDK unavailable", e) }
    }

    private fun executeInstalled(action: RahaAction, p: Map<String, Any>) {
        val apk = File("/system/priv-app/Setting-release-signed/Setting-release-signed.apk")
        check(apk.isFile) { "Installed display SDK unavailable" }
        val loader = object : PathClassLoader(apk.path, javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.system.") && !name.startsWith("com.qinggan.canbus.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        fun service(name: String, api: String): Pair<Class<*>, Any> {
            val type = loader.loadClass(api)
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, name) as? IBinder ?: error("Missing service $name")
            check(binder.isBinderAlive && binder.interfaceDescriptor == api) { "Invalid service $name" }
            val remote = loader.loadClass(api + "\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            return type to checkNotNull(remote)
        }
        val (powerType, power) = service("qg.power_manager", "com.qinggan.system.IPowerManagerService")
        val temperature = powerType.getMethod("getLCDTemp").invoke(power) as Int
        check(temperature >= 0 && temperature !in 1..3) { "LCD thermal protection active/unavailable: $temperature" }
        val (type, remote) = service("qg.system_policy", "com.qinggan.system.ISystemPolicy")
        val intType = Int::class.javaPrimitiveType!!
        fun read(method: String, id: Int) = type.getMethod(method, intType).invoke(remote, id) as Int
        fun write(method: String, value: Int, id: Int, verifyFeedbackOnFalse: Boolean = false) {
            val accepted = type.getMethod(method, intType, intType, intType, Boolean::class.javaPrimitiveType)
                .invoke(remote, value, 1, id, false) as Boolean
            AppLog.info("RahaDisplay", "route=binder method=$method id=$id value=$value accepted=$accepted")
            // OEM setDimmer can return false after applying a multi-screen change.
            // Only that path may continue: every target must still pass readback below.
            check(accepted || verifyFeedbackOnFalse) { "Display service rejected $method" }
        }
        fun confirm(method: String, id: Int, expected: Int) {
            val deadline = SystemClock.elapsedRealtime() + 2000
            var actual = read(method, id)
            while (actual != expected && SystemClock.elapsedRealtime() < deadline) { Thread.sleep(50); actual = read(method, id) }
            AppLog.info("RahaDisplay", "feedback $method id=$id expected=$expected actual=$actual")
            check(actual == expected) { "Display feedback mismatch id=$id expected=$expected actual=$actual" }
        }
        if (action == RahaAction.SET_DISPLAY_THEME_MODE) {
            val value = mapOf("day" to 1, "night" to 0, "auto" to 2).getValue(p["mode"] as String)
            write("setDimmer", value, 19, verifyFeedbackOnFalse = true)
            for (id in listOf(1, 2, 16)) confirm("getDimmer", id, value)
            return
        }
        require(action == RahaAction.SET_DISPLAY_BRIGHTNESS)
        val ids = mapOf("central" to 1, "passenger" to 2, "cluster" to 16)
        val targets = if (p["display"] == "all") ids.values.toList() else listOf(ids.getValue(p["display"] as String))
        val plans = targets.map { id ->
            val current = read("getBackLight", id)
            check(current in 0..100) { "Display brightness unavailable id=$id: $current" }
            val value = if (p.containsKey("percent")) 1 + ((p.getValue("percent") as Number).toDouble() * 99 / 100).roundToInt()
                else current + (p.getValue("deltaPercent") as Number).toInt()
            id to value.coerceIn(1, 100)
        }
        // Resolve cluster SDK before any writes; do not replay partial commands on failure.
        val can = if (16 in targets) service("qg.canbus", "com.qinggan.canbus.ICanBusService") else null
        val stateType = if (can != null) loader.loadClass("com.qinggan.canbus.VehicleState") else null
        val field = if (can != null) loader.loadClass("com.qinggan.canbus.DFVehicleState").getField("ICM_SCR_BRI_LEVEL").get(null) else null
        for ((id, value) in plans) {
            write("setBackLight", value, id)
            if (id == 16) {
                val level = if (read("getDimmer", 1) == 1) 10 else (value - 1) / 10 + 1
                val result = can!!.first.getMethod("setVehicleState", stateType, intType).invoke(can.second, field, level)
                check(result !is Number || result.toInt() >= 0) { "Cluster brightness rejected: $result" }
                AppLog.info("RahaDisplay", "cluster level=$level result=$result")
            }
            confirm("getBackLight", id, value)
        }
    }
}
