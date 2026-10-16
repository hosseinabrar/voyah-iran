package com.abrar.voyahiran.core.bridge

import android.os.Binder
import android.os.Parcel
import android.os.SystemClock
import android.util.AtomicFile
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.feature.dow.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock

/** Resident worker: no shell/bridge requests per pulse; same write lock as other vehicle commands. */
internal object DowAmbientBridge {
    @Volatile private var until = 0L
    private val manualTest = DowManualTest() // Accessed under the bridge execution lock.
    @Volatile private var dirty = true
    @Volatile private var status = "idle"
    private var sdk: RahaVehicleBridge.OemSession? = null
    private var engine: DowAmbientEngine? = null
    @Volatile private var dowIdentity: Pair<Int, Int>? = null
    private var nextRead = 0L
    private var acc: Int? = null
    private var dow: Int? = null
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString("com.qinggan.canbus.ICanBusServiceCallback"); return true }
            data.enforceInterface("com.qinggan.canbus.ICanBusServiceCallback")
            if (code == 0x27) dirty = true
            if (code == 0x24 && data.readInt() != 0) {
                val ordinal = data.readInt(); val id = data.readInt()
                if (dowIdentity == (ordinal to id)) dirty = true
            }
            return true
        }
    }
    fun command(value: String): CommandResult {
        if (value == "test-stop") {
            manualTest.stop()
            return CommandResult(0, "DOW_TEST_STOPPED; real warnings remain active")
        }
        if (value == "test-start") {
            if (!BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP inactive")
            val vehicle = sdk ?: RahaVehicleBridge.OemSession()
            if (!manualTest.start(SystemClock.elapsedRealtime(), BridgeSubscriptionSession.vipActive, vehicle.acc(), vehicle.speed()))
                return CommandResult(126, "Manual test requires powered stationary vehicle")
            return CommandResult(0, "DOW_TEST_STARTED; expires in 10 seconds")
        }
        if (value == "enable") {
            if (!BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP inactive")
            until = SystemClock.elapsedRealtime() + 15_000
        } else if (value == "disable") until = 0
        else return CommandResult(0, status)
        return CommandResult(0, "DOW_ACCEPTED")
    }
    fun start(lock: ReentrantLock) {
        Thread({
            var retryAt = 0L
            while (true) {
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now >= retryAt && lock.tryLock()) {
                        try {
                            if (engine == null) engine = DowAmbientEngine(Port(), Journal())
                            val entitled = BridgeSubscriptionSession.vipActive
                            val automatic = now < until && entitled
                            var manual = manualTest.pending(now, entitled)
                            val active = automatic || manual
                            // Pending rollback runs without VIP: it only restores our own saved settings.
                            if (active || engine!!.journal?.pending == true || engine!!.flashing) {
                                if (sdk == null) {
                                    sdk = RahaVehicleBridge.OemSession().also {
                                        dowIdentity = it.stateIdentity("DOW_STATE")
                                        it.watch(callback, true)
                                    }
                                    dirty = true
                                }
                                if (dirty || now >= nextRead) {
                                    dirty = false; nextRead = now + 1000
                                    acc = session().acc().takeIf { it == 0 || it == 2 }
                                    dow = session().vehicle("DOW_STATE").takeIf { it in 0..7 }
                                    AppLog.changed("DowAmbient", "acc=$acc state=$dow trigger=3 EXPERIMENTAL")
                                }
                                if (manual) manual = manualTest.active(now, entitled, acc, session().speed())
                                engine!!.tick(now, automatic || manual, acc,
                                    if (manual) DowContract.EXPERIMENTAL_WARNING else if (automatic) dow else null)
                                status = when {
                                    engine!!.flashing -> "flashing"
                                    engine!!.journal?.pending == true -> "restore-pending"
                                    else -> "listening"
                                }
                            } else if (sdk != null) {
                                runCatching { sdk?.watch(callback, false) }; sdk = null; status = "idle"
                            }
                        } finally { lock.unlock() }
                    }
                } catch (e: Throwable) {
                    if (e is ThreadDeath || e is VirtualMachineError) throw e
                    status = "error: " + (e.message ?: e.javaClass.simpleName)
                    AppLog.error("DowAmbient", "worker failed; original snapshot retained", e)
                    engine?.connectionLost()
                    runCatching { sdk?.watch(callback, false) }; sdk = null
                    acc = null; dow = null; retryAt = SystemClock.elapsedRealtime() + 5000
                }
                Thread.sleep(25)
            }
        }, "voyah-dow-ambient").apply { isDaemon = true; start() }
    }
    private fun session() = checkNotNull(sdk)
    private class Journal : AmbientStore {
        private val file = AtomicFile(File("/data/local/voyahiran-dow-ambient.json"))
        override fun read(): AmbientJournal? {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
            val json = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            check(json.getInt("version") == 1)
            val s = AmbientSnapshot(json.getInt("power"), json.getInt("music"), json.getInt("drive"),
                json.getInt("color"), json.getInt("brightness"))
            check(s.valid()) { "Invalid saved ambient state; refuse overwrite" }
            return AmbientJournal(s, json.getBoolean("pending"))
        }
        override fun write(value: AmbientJournal) {
            check(value.snapshot.valid())
            val s = value.snapshot
            val bytes = JSONObject().put("version", 1).put("pending", value.pending).put("power", s.power)
                .put("music", s.music).put("drive", s.drive).put("color", s.color).put("brightness", s.brightness)
                .toString().toByteArray()
            val stream = file.startWrite()
            try { stream.write(bytes); stream.fd.sync(); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
    }
    private class Port : AmbientPort {
        private val prefix = "VEHICLE_AMBIENT_LIGHT_"
        private fun drive(value: Int) = DowAmbientSettings.set(value)
        override fun snapshot() = AmbientSnapshot(
            session().vehicle(prefix + "SWITCH"), session().vehicle(prefix + "MUSICAL_RHYTHM_SWITCH"),
            DowAmbientSettings.read(), session().vehicle(prefix + "COLOR"), session().vehicle(prefix + "BRIGHTNESS_LEVEL"))
        override fun normalRed() {
            val started = SystemClock.elapsedRealtime()
            check(BridgeSubscriptionSession.vipActive)
            // Driving synchronisation is an Android preference, not a CAN bundle field.
            drive(0)
            session().writeAmbientBundle(DowAmbientBundle.warning())
            AppLog.info("DowAmbient", "normal red configured elapsedMs=${SystemClock.elapsedRealtime() - started}")
        }
        override fun level(value: Int) {
            check(BridgeSubscriptionSession.vipActive)
            require(value == 0 || value == 10)
            session().writeAmbientBundle(DowAmbientBundle.warning(value))
        }
        override fun restore(snapshot: AmbientSnapshot) {
            check(snapshot.valid())
            drive(snapshot.drive)
            session().writeAmbientBundle(DowAmbientBundle.restore(snapshot))
            AppLog.info("DowAmbient", "restore sent; awaiting feedback")
        }
        override fun confirmed(snapshot: AmbientSnapshot) = snapshot() == snapshot
    }
}
