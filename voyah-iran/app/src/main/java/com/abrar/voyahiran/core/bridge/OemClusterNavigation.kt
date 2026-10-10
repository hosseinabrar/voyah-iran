package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Single owner with a short lease: loss of the app/listener cannot leave stale guidance. */
internal object OemClusterNavigation {
    private val lock = Any()
    private var frame: ClusterFrame? = null
    private var lastRefresh = 0L
    private var owned = false
    private var started = false
    private var api: Api? = null
    private var sentFrame: ClusterFrame? = null
    private var sentPlaceholder = false
    private var lastSentAt = 0L
    fun command(arguments: List<String>): CommandResult = synchronized(lock) {
        if (arguments[1] == "show" && !BridgeSubscriptionSession.vipActive)
            return CommandResult(126, "Subscription runtime inactive")
        frame = if (arguments[1] == "show") ClusterFrame(arguments[2], arguments[3].toInt(), arguments[4].toInt()) else null
        lastRefresh = SystemClock.elapsedRealtime()
        if (!started) {
            started = true
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "VoyahClusterNavigation").apply { isDaemon = true } }
                .scheduleWithFixedDelay(::tick, 0, 200, TimeUnit.MILLISECONDS)
        }
        CommandResult(0, "Cluster navigation queued")
    }
    private fun tick() = synchronized(lock) {
        val next = frame?.takeIf { BridgeSubscriptionSession.vipActive && SystemClock.elapsedRealtime() - lastRefresh < 3500 }
        if (next == null) ClusterPlaceholder.close()
        if (next == null && !owned) return@synchronized
        try {
            val placeholder = if (next != null && next.meters < 0) ClusterPlaceholder.available() else { ClusterPlaceholder.close(); false }
            val effective = next?.displayValue()
            val alive = api?.binder?.isBinderAlive == true
            // The OEM cluster expires unchanged guidance unless it receives a keepalive.
            // Refresh only a still-valid leased frame; never revive expired source data.
            if (owned && alive && effective == sentFrame && placeholder == sentPlaceholder &&
                SystemClock.elapsedRealtime() - lastSentAt < 1000) return@synchronized
            val client = api?.takeIf { alive } ?: Api().also { api = it }
            // Mark ownership before dispatch: even an uncertain Binder result requires cleanup.
            owned = true
            client.send(effective, placeholder)
            lastSentAt = SystemClock.elapsedRealtime()
            sentFrame = effective; sentPlaceholder = placeholder
            owned = next != null
            AppLog.info("NeshanClusterBridge", "OEM update active=$owned candidateTbtIcon=${next?.icon} meters=${next?.meters}; Binder returned; experimental mapping")
        } catch (e: Exception) {
            api = null
            AppLog.info("NeshanClusterBridge", "OEM update failed ${e.javaClass.simpleName}: ${e.message}")
        } catch (e: LinkageError) {
            api = null
            AppLog.changed("NeshanClusterBridge", "OEM SDK unavailable: ${e.message}")
        }
    }
    private class Api {
        val binder: IBinder
        private val loader: ClassLoader
        private val remote: Any
        private val infoType: Class<*>
        private val guideType: Class<*>
        private val update: java.lang.reflect.Method
        init {
            val apk = File("/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk")
            check(apk.isFile) { "Installed vehicle SDK not found" }
            loader = object : PathClassLoader(apk.path, "/system/lib64:/system/lib", javaClass.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (!name.startsWith("com.qinggan.cluster.")) return super.loadClass(name, resolve)
                    synchronized(this) {
                        val type = findLoadedClass(name) ?: findClass(name)
                        if (resolve) resolveClass(type)
                        return type
                    }
                }
            }
            val apiType = loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerService")
            binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "qg.cluster.service") as? IBinder ?: error("Cluster service unavailable")
            check(binder.interfaceDescriptor == apiType.name) { "Cluster descriptor mismatch" }
            remote = checkNotNull(loader.loadClass("com.qinggan.cluster.IInstrumentClusterManagerService\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder))
            infoType = loader.loadClass("com.qinggan.cluster.info.NaviInfo")
            guideType = loader.loadClass("com.qinggan.cluster.info.GuideState")
            update = apiType.getMethod("updateNaviInfo", infoType)
        }
        fun send(frame: ClusterFrame?, placeholder: Boolean) {
            val info = infoType.getConstructor().newInstance()
            val missingDistance = frame != null && frame.meters < 0
            val guided = frame != null && (!missingDistance || placeholder)
            val road = frame?.text?.ifBlank { "-" }.orEmpty()
            // The stock SDK formats every negative integer as zero. If the small formatter
            // cannot attach, show truthful text instead of claiming that the next turn is 0 m away.
            val fallback = if (frame != null && missingDistance && !placeholder) "$road | -" else road
            if (missingDistance && !placeholder) AppLog.changed("NeshanClusterBridge", "Missing-distance formatter unavailable; text fallback (direction temporarily unavailable)")
            val stateName = if (frame == null) "MAN_STOP" else if (guided) "GUIDING" else "ROAM"
            val state = checkNotNull(guideType.enumConstants).first { (it as Enum<*>).name == stateName }
            infoType.getMethod("setGuideState", guideType).invoke(info, state)
            infoType.getMethod("setCurrentRoadName", String::class.java).invoke(info, if (guided) "" else fallback)
            infoType.getMethod("setTurnRoadName", String::class.java).invoke(info, if (guided) road else "")
            if (guided) {
                infoType.getMethod("setTbtIconId", Int::class.javaPrimitiveType).invoke(info, frame.icon)
                val displayMeters = if (missingDistance) ClusterPlaceholder.MISSING_DISTANCE else ClusterDistance.floorMeters(frame.meters)
                AppLog.changed("NeshanClusterDistance", "sourceMeters=${frame.meters} displayMeters=$displayMeters")
                infoType.getMethod("setNextDistance", Int::class.javaPrimitiveType).invoke(info, displayMeters)
            }
            update.invoke(remote, info)
        }
    }
}
