package com.abrar.voyahiran.core.bridge

import android.os.Bundle
import android.os.IBinder
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.feature.apollo.domain.ApolloPolicy
import dalvik.system.PathClassLoader
import java.io.File

internal object ApolloBundle {
    fun apply(requested: Int): CommandResult = dispatch(requested, null)
    fun reconcile(requested: Int): CommandResult = dispatch(requested, null, reconcile = true)
    // Keep SDK classes resident instead of creating a new loader every ten seconds.
    private val sdkLoader by lazy {
        val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
        check(apk.isFile) { "Vehicle SDK unavailable" }
        object : PathClassLoader(apk.path, "/system/lib64:/system/lib", javaClass.classLoader) {
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
    fun setFeature(name: String, enabled: Boolean): CommandResult {
        val feature = com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.valueOf(name)
        return dispatch(if (enabled) feature.on else feature.off, feature)
    }
    private fun dispatch(requested: Int, feature: com.abrar.voyahiran.feature.apollo.domain.ApolloFeature?, reconcile: Boolean = false): CommandResult {
      return try {
        require(requested in 1..2)
        check(feature == null || BridgeSubscriptionSession.vipActive) { "Voyah Iran subscription inactive" }
        val subscriptionActive = BridgeSubscriptionSession.vipActive
        val value = if (feature == null) ApolloPolicy.value(subscriptionActive, requested == 2) else requested
        if (reconcile) AppLog.info("Apollo", "reconcile begin requested=$requested subscriptionActive=$subscriptionActive expectedSet=$value expectedRaw=${value - 1}")
        val loader = sdkLoader
        val facade = loader.loadClass("com.qinggan.canbus.DFVehicleState")
        val api = loader.loadClass("com.qinggan.canbus.ICanBusService")
        // Resolve all fields before any write. Never send a partial feature set.
        val bundle = if (feature == null) Bundle().apply { ApolloPolicy.fields.forEach { putInt(checkNotNull(facade.getField(it).get(null)).toString(), value) } } else null
        val state = feature?.let { checkNotNull(facade.getField(it.field).get(null)) }
        val stateType = loader.loadClass("com.qinggan.canbus.VehicleState")
        val method = if (feature == null) api.getMethod("setVehicleAndAirConditionBundleState", Bundle::class.java, Bundle::class.java)
            else api.getMethod("setVehicleState", stateType, Int::class.javaPrimitiveType)
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "qg.canbus") as? IBinder ?: error("CAN service unavailable")
        check(binder.isBinderAlive && binder.interfaceDescriptor == api.name) { "CAN descriptor mismatch" }
        val remote = loader.loadClass("com.qinggan.canbus.ICanBusService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        if (reconcile) {
            val getter = api.getMethod("getVehicleState", stateType)
            val actual = run {
                try { (getter.invoke(remote, facade.getField("PLC_FUNC_ENABLE_SA").get(null)) as Number).toInt() }
                catch (e: Exception) {
                    AppLog.error("Apollo", "reconcile read failed field=PLC_FUNC_ENABLE_SA", e)
                    null
                }
            }
            // A missing cache entry (-1) explicitly requests resending the desired bundle.
            // A read exception (null) is kept distinct from that valid sentinel.
            val write = com.abrar.voyahiran.feature.apollo.domain.ApolloReconcile.needsWrite(value, actual)
            val reason = when {
                actual == null -> "read_failed"
                actual == -1 -> "cache_missing_resend"
                actual !in 0..1 -> "unexpected_raw_value"
                write -> "cached_state_mismatch"
                else -> "cached_state_matches"
            }
            AppLog.info("Apollo", "reconcile field=PLC_FUNC_ENABLE_SA actualRaw=$actual expectedRaw=${value - 1} expectedSet=$value action=${if (write) "send_bundle" else "skip"} reason=$reason")
            if (!write) return CommandResult(0, "Apollo bundle checked value=$value actualRaw=$actual reason=$reason")
        }
        val result = if (feature == null) method.invoke(remote, null, bundle) else method.invoke(remote, state, value)
        check(result !is Number || result.toInt() >= 0) { "OEM rejected Apollo bundle: $result" }
        if (feature != null) {
            val getter = api.getMethod("getVehicleState", stateType)
            val deadline = android.os.SystemClock.elapsedRealtime() + 2000
            var actual = getter.invoke(remote, state) as Int
            while (actual != value && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100)
                actual = getter.invoke(remote, state) as Int
            }
            AppLog.info("Apollo", "field=${feature.field} expected=$value actual=$actual")
            check(actual == value) { "Vehicle feedback mismatch: ${feature.field} expected=$value actual=$actual" }
        }
        AppLog.info("Apollo", "dispatched reconcile=$reconcile field=${feature?.field ?: "bundle"} fields=${if (feature == null) ApolloPolicy.fields.size else 1} value=$value result=$result (dispatch only, not ECU confirmation)")
        CommandResult(0, "Apollo bundle dispatched value=$value")
    } catch (e: Exception) {
        AppLog.error("Apollo", "Dispatch failed reconcile=$reconcile requested=$requested", e)
        CommandResult(1, "Apollo bundle failed: ${e.javaClass.simpleName}: ${e.message}")
    }
    }
}
