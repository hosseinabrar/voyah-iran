package com.abrar.voyahiran.core.bridge

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import dalvik.system.PathClassLoader
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import com.abrar.voyahiran.core.bridge.AvmTurnPolicy.*

/** One monitor in the root bridge, independent of Activity lifetime. Never changes the OEM trigger bit. */
internal object AvmTurnBridge {
    @Volatile private var enabled = false
    @Volatile private var running = false
    @Volatile private var status = "idle"
    private val events = ConcurrentLinkedQueue<Pair<Any, Int>>()

    @Synchronized fun execute(operation: String): CommandResult = try {
        if (operation == "on") {
            check(BridgeSubscriptionSession.active) { "Subscription inactive" }
            if (!enabled) {
                val equipment = ApolloCapability.read()
                check(equipment.exitCode == 0 && equipment.output == "APOLLO_CAPABILITY sdb=1") { "Baidu equipment not confirmed" }
                enabled = true
                AppLog.info("AvmTurn", "automatic monitor enabled")
            }
            if (!running) {
                running = true
                Thread(::monitor, "VoyahAvmTurn").apply { isDaemon = true; start() }
            }
        } else if (operation == "off") {
            if (enabled) AppLog.info("AvmTurn", "automatic monitor disabled")
            enabled = false
        }
        CommandResult(0, "AVM_TURN actual=${if (enabled) 2 else 1} monitor=$status")
    } catch (e: Exception) {
        AppLog.error("AvmTurn", "configure failed", e)
        CommandResult(1, "AVM_TURN configuration failed")
    }

    private fun monitor() {
        var client: Client? = null
        var policy = AvmTurnPolicy()
        var visible = Visibility.UNKNOWN
        var lastInput: Input? = null
        while (true) {
            try {
                if (client == null && (!enabled || !BridgeSubscriptionSession.active)) {
                    Thread.sleep(500); continue
                }
                val c = client ?: Client().also {
                    client = it
                    visible = it.initialVisibility()
                    status = "ready"
                    AppLog.info("AvmTurn", "connected initialVisibility=$visible")
                }
                fun drain() {
                    while (true) {
                        val event = events.poll() ?: break
                        if (event.first !== c) continue
                        visible = when (event.second) { 0 -> Visibility.HIDDEN; 1 -> Visibility.USER; 2 -> Visibility.REVERSE; else -> Visibility.UNKNOWN }
                        policy.visibilityChanged(visible)
                        AppLog.info("AvmTurn", "AVM callback=${event.second} visibility=$visible")
                    }
                }
                drain()
                val input = c.input(enabled, visible)
                drain()
                val fresh = input.copy(visibility = visible)
                if (fresh != lastInput) {
                    // Log only relevant changes; speed is included when another condition changes.
                    if (lastInput == null || fresh.copy(speedKmh=lastInput.speedKmh) != lastInput)
                        AppLog.info("AvmTurn", "input=$fresh")
                    lastInput = fresh
                }
                val action = policy.step(fresh, SystemClock.elapsedRealtime())
                if (action != null) {
                    // Re-check reverse immediately before every operation, after callback delivery.
                    drain()
                    if (c.reverse() != false || visible == Visibility.REVERSE) {
                        policy.visibilityChanged(Visibility.REVERSE)
                    } else if (c.pdc() != false) {
                        policy.selectDeferred()
                        AppLog.info("AvmTurn", "operation skipped: PDC active or unknown")
                    } else when (action) {
                        is Action.Open -> {
                            if (visible == Visibility.HIDDEN && !c.visibilityChangedSinceRead && enabled && BridgeSubscriptionSession.active && c.speed() in 0..24) {
                                val result = c.openFromLauncher()
                                policy.openResult(result == 0, SystemClock.elapsedRealtime())
                                AppLog.info("AvmTurn", "open side=${action.side} result=$result")
                            }
                        }
                        is Action.Select -> {
                            if (visible == Visibility.USER && !c.visibilityChangedSinceRead && enabled && BridgeSubscriptionSession.active && c.speed() in 0..24) {
                                val result = c.select(action.side)
                                policy.selectResult(result)
                                AppLog.info("AvmTurn", "select side=${action.side} dispatched=$result")
                            } else policy.selectDeferred()
                        }
                        Action.Close -> if (visible == Visibility.USER) {
                            // A hide/reopen in the queue must not close a new manual session.
                            if (fresh.visibility == visible && !c.visibilityChangedSinceRead) {
                                AppLog.info("AvmTurn", "close after owned session dispatched=${c.closeFromButton()}")
                            }
                        }
                    }
                }
                if (!enabled || !BridgeSubscriptionSession.active) {
                    c.close(); client = null; policy = AvmTurnPolicy(); visible = Visibility.UNKNOWN; status = "idle"
                }
                Thread.sleep(200)
            } catch (e: Exception) {
                status = "retry"
                AppLog.error("AvmTurn", "monitor failed; ownership discarded, retry in 5s", e)
                runCatching { client?.close() }
                client = null; policy = AvmTurnPolicy(); visible = Visibility.UNKNOWN
                Thread.sleep(5_000)
            } catch (e: LinkageError) {
                status = "unsupported"
                AppLog.error("AvmTurn", "OEM SDK unavailable", e)
                runCatching { client?.close() }; client = null
                policy = AvmTurnPolicy(); visible = Visibility.UNKNOWN
                Thread.sleep(5_000)
            }
        }
    }

    private class Client {
        private val loader = object : PathClassLoader(
            "/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk",
            "/system/lib64:/system/lib", AvmTurnBridge::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.qinggan.canbus.") && !name.startsWith("com.qinggan.system.")) return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }
        private fun service(name: String, api: Class<*>): Any {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null,name) as? IBinder ?: error("$name unavailable")
            check(binder.isBinderAlive && binder.interfaceDescriptor == api.name) { "$name descriptor mismatch" }
            return loader.loadClass(api.name + "\$Stub").getMethod("asInterface",IBinder::class.java).invoke(null,binder)
                ?: error("$name proxy unavailable")
        }
        private val canApi = loader.loadClass("com.qinggan.canbus.ICanBusService")
        private val can = service("qg.canbus", canApi)
        private val stateType = loader.loadClass("com.qinggan.canbus.VehicleState")
        private val getter = canApi.getMethod("getVehicleState",stateType)
        private val systemApi = loader.loadClass("com.qinggan.system.ISystemPolicy")
        private val system = service("qg.system_policy",systemApi)
        private val callbackType = loader.loadClass("com.qinggan.system.IReverseCallback")
        @Volatile private var revision = 0L
        private var readRevision = 0L
        val visibilityChangedSinceRead get() = revision != readRevision
        private val callbackBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) { reply?.writeString(callbackType.name); return true }
                if (code in 1..4) {
                    data.enforceInterface(callbackType.name)
                    val value = data.readInt()
                    if (code == 4) { revision++; events.add(this@Client to value) }
                    if (code == 3) revision++ // Invalidate a pending operation on any PDC transition.
                    // Actual transmission gear is read synchronously before every operation.
                    return true
                }
                return super.onTransact(code,data,reply,flags)
            }
        }
        private val callback = Proxy.newProxyInstance(loader,arrayOf(callbackType)) { proxy, method, args ->
            when(method.name) {
                "asBinder" -> callbackBinder
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "VoyahAvmTurnCallback"
                else -> null
            }
        }
        private val native = AvmNativeStateReader()
        private var openedAt = Long.MAX_VALUE
        init {
            try { check(systemApi.getMethod("registerCallback",callbackType).invoke(system,callback) == true) { "AVM callback registration failed" } }
            catch (e: Exception) { native.close(); throw e }
        }
        fun close() {
            try { systemApi.getMethod("unregisterCallback",callbackType).invoke(system,callback) }
            finally { native.close() }
        }
        private fun state(name: String) = (getter.invoke(can,stateType.getField(name).get(null)) as Number).toInt()
        fun initialVisibility() = when(state("APA_AVM_STS")) { 0 -> Visibility.HIDDEN; 2 -> Visibility.USER; else -> Visibility.UNKNOWN }
        fun speed() = (canApi.getMethod("getVehicleSpeed").invoke(can) as Number).toInt()
        fun reverse(): Boolean? {
            // ReverseManager's state also becomes 1 for manually opened AVM on H97X.
            // Use transmission gear, not camera visibility, to protect reverse priority.
            val gear = canApi.getMethod("getGearStatus").invoke(can) as? Enum<*>
            return when (gear?.name) {
                "Reverse" -> true
                "Parking", "Neutral", "Drive", "Battery" -> false
                else -> null
            }
        }

        fun pdc(): Boolean? = when ((systemApi.getMethod("getPdcState").invoke(system) as Number).toInt()) {
            0 -> false
            1 -> true
            else -> null
        }
        fun input(enabled: Boolean, visible: Visibility): Input {
            check(!native.failed) { "Native AVM receiver unavailable" }
            readRevision = revision
            fun switch(name: String): Boolean? = when(state(name)) { 1 -> false; 2 -> true; else -> null }
            return Input(enabled,BridgeSubscriptionSession.active,reverse(),speed().takeIf { it>=0 },
                switch("LEFT_DIRECTION_LIGHT"),switch("RIGHT_DIRECTION_LIGHT"),visible,pdc())
        }
        fun openFromLauncher(): Int {
            openedAt = SystemClock.elapsedRealtime()
            val request = loader.loadClass("com.qinggan.canbus.DFVehicleState")
                .getField("IVI_REQ_AVM_SW").get(null)
            canApi.getMethod("setVehicleState", stateType, Int::class.javaPrimitiveType)
                .invoke(can, request, 1)
            // Binder completion is dispatch only; policy waits for the visible callback.
            AppLog.info("AvmTurn", "launcher open IVI_REQ_AVM_SW=1 dispatched")
            return 0
        }
        fun closeFromButton(): Boolean {
            if (!safeToClose()) return false
            // Native close button verified in the user's manual close capture.
            // Closing must work after the turn signal is off, unlike camera selection.
            return tap(60, 75, selecting = false)
        }
        private fun safeToClose(): Boolean {
            val sample = native.sample ?: return false
            return !native.failed && sample.at >= openedAt && sample.frame.active &&
                !visibilityChangedSinceRead && reverse() == false && pdc() == false && speed() in 0..24
        }
        private var selectingSide: Side? = null
        private fun safeToSelect(): Boolean {
            if (!enabled || !BridgeSubscriptionSession.active || native.failed || visibilityChangedSinceRead ||
                reverse() != false || pdc() != false || speed() !in 0..24) return false
            val left=state("LEFT_DIRECTION_LIGHT")
            val right=state("RIGHT_DIRECTION_LIGHT")
            return when(selectingSide) {
                Side.LEFT -> left == 2 && right == 1
                Side.RIGHT -> right == 2 && left == 1
                Side.FRONT -> left == 1 && right == 1
                null -> false
            }
        }

        private fun awaitFrame(afterSequence: Long = -1, predicate: (AvmNativeFrame) -> Boolean): AvmNativeStateReader.Sample? {
            val deadline = SystemClock.elapsedRealtime() + 1800
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!safeToSelect()) return null
                val sample = native.sample
                if (sample != null && sample.at >= openedAt && sample.sequence > afterSequence && sample.frame.active && predicate(sample.frame)) return sample
                Thread.sleep(100)
            }
            return null
        }
        private fun tap(x: Int, y: Int, selecting: Boolean = true): Boolean {
            if (selecting) {
                if (!safeToSelect()) return false
            } else if (!safeToClose()) return false
            val method=systemApi.getMethod("touchEvent",*Array(6) { Int::class.javaPrimitiveType!! })
            fun touch(kind: Int) = (method.invoke(system,kind,x,y,0,0,0) as Number).toInt()
            val down=touch(0)
            val up: Int
            try { Thread.sleep(80) } finally { up=touch(1) }
            return down == 0 && up == 0
        }
        fun select(side: Side): Boolean {
            selectingSide = side
            val initial = awaitFrame { true } ?: run {
                AppLog.info("AvmTurn", "selection skipped: no current native AVM state or higher-priority camera activity")
                return false
            }
            // Preserve the user's current 2D/3D mode. No mode-toggle touch is sent.
            val current = native.sample ?: return false
            if (!current.frame.active || current.frame.twoDimensional != initial.frame.twoDimensional) return false
            val target = current.frame.targetView(side)
            if (current.frame.view == target) return true
            if (!safeToSelect()) return false
            // Native getViewSelect mapping includes 3D south-west=11 and south-east=12.
            // Dispatch a native view request; success still requires frame confirmation.
            val result = (systemApi.getMethod("sendMsg", *Array(3) { Int::class.javaPrimitiveType!! })
                .invoke(system, 14, target, 0) as Number).toInt()
            AppLog.info("AvmTurn", "view request mode=${if (current.frame.twoDimensional) "2D" else "3D"} side=$side sendMsg(14,$target,0) result=$result")
            if (result != 0) return false
            val confirmed = awaitFrame(current.sequence) { it.twoDimensional == current.frame.twoDimensional && it.view == target } != null
            AppLog.info("AvmTurn", "side=$side native confirmation=$confirmed")
            return confirmed
        }
    }
}
