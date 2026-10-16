package com.abrar.voyahdowtest

import android.app.Activity
import android.content.*
import android.os.*
import android.util.Log
import android.widget.Toast
import com.abrar.voyahiran.feature.dow.DowContract
import java.io.File
import java.util.ArrayDeque

/** CAN monitoring is read-only; explicit light test delegates to the authenticated main bridge. */
class MainActivity : Activity() {
    private lateinit var ui: ProbeView
    private val main = Handler(Looper.getMainLooper())
    private val workerThread = HandlerThread("dow-probe")
    private lateinit var worker: Handler
    private var binder: IBinder? = null
    private var bound = false
    @Volatile private var dowIdentity: Pair<Int, Int>? = null
    @Volatile private var switchIdentity: Pair<Int, Int>? = null
    @Volatile private var visible = false
    @Volatile private var target = 3
    @Volatile private var state: Int? = null
    @Volatile private var switch: Int? = null
    @Volatile private var acc: Int? = null
    private var flashStarted = 0L
    private var wasMatch = false
    private val recent = ArrayDeque<String>()
    private lateinit var logFile: File
    private var lastSample = ""
    private var lastHeartbeat = 0L
    private var manualTestRequested = false
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CALLBACK); return true }
            data.enforceInterface(CALLBACK)
            if (code == 0x24 && data.readInt() != 0) {
                val ordinal = data.readInt(); val id = data.readInt(); val value = data.readInt()
                if (dowIdentity == (ordinal to id))
                    worker.post { if (visible) { state = value; record("EVENT DOW_STATE=" + value); display() } }
                if (switchIdentity == (ordinal to id))
                    worker.post { if (visible) { switch = value; record("EVENT DOW_SWITCH=" + value); display() } }
            } else if (code == 0x27) {
                val value = data.readInt()
                worker.post { if (visible) { acc = value; record("EVENT ACC=" + value); display() } }
            }
            return true
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            worker.post {
                if (!visible) return@post
                try {
                    check(service.interfaceDescriptor == DESCRIPTOR)
                    resolveFields()
                    binder = service
                    check(call(0x1c, { writeStrongBinder(callback) }) != 0)
                    record("CONNECTED " + Build.MODEL + " sdk=" + Build.VERSION.SDK_INT)
                    worker.removeCallbacks(poll); poll.run()
                } catch (e: Exception) { disconnect(e.toString()) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { worker.post { disconnect("service disconnected") } }
        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName) { worker.post { disconnect("null binding") } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workerThread.start(); worker = Handler(workerThread.looper)
        logFile = File(getExternalFilesDir(null) ?: filesDir, "dow-probe.log")
        target = getPreferences(0).getInt("target", 3).coerceIn(0, 7)
        ui = ProbeView(this); ui.target.setText(target.toString()); setContentView(ui)
        ui.testStart.setOnClickListener {
            manualTestRequested = true
            ui.testStart.isEnabled = false
            worker.post { sendLightTest(true) }
        }
        ui.testStop.setOnClickListener {
            manualTestRequested = false
            worker.post { sendLightTest(false) }
        }
        ui.apply.setOnClickListener {
            val value = ui.target.text.toString().toIntOrNull()
            if (value == null || value !in 0..7) { ui.target.error = "Enter 0–7"; return@setOnClickListener }
            target = value; getPreferences(0).edit().putInt("target", target).apply()
            wasMatch = false
            worker.post { record("TARGET=" + target + " (4 means fault, not danger)"); display() }
        }
        ui.export.setOnClickListener {
            try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "dow-" + System.currentTimeMillis() + ".log"), 12) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, "Use Share log or adb pull: " + logFile.path, Toast.LENGTH_LONG).show() }
        }
        ui.share.setOnClickListener {
            worker.post {
                val text = runCatching { logFile.readText().takeLast(200_000) }.getOrDefault("No samples")
                main.post { runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text), "DOW log")) } }
            }
        }
    }
    override fun onStart() {
        super.onStart(); visible = true
        worker.post { record("START target=" + target + " file=" + logFile.path) }
        try {
            bound = bindService(Intent().setComponent(ComponentName("com.qinggan.canbus.service",
                "com.qinggan.canbus.service.CanBusService")), connection, Context.BIND_AUTO_CREATE)
            if (!bound) worker.post { disconnect("bind rejected") }
        } catch (e: Exception) { worker.post { disconnect(e.toString()) } }
        main.post(frame)
    }
    override fun onStop() {
        visible = false; main.removeCallbacks(frame); worker.removeCallbacks(poll)
        if (manualTestRequested) {
            manualTestRequested = false
            worker.post { sendLightTest(false) }
        }
        worker.post {
            runCatching { call(0x1d) { writeStrongBinder(callback) } }; binder = null
            state = null; switch = null; acc = null; record("STOP")
        }
        if (bound) { unbindService(connection); bound = false }
        super.onStop()
    }
    override fun onDestroy() { workerThread.quitSafely(); super.onDestroy() }
    private fun sendLightTest(start: Boolean) {
        val result = runCatching { AmbientTestClient.send(start) }
        val message = result.getOrElse { "Light test failed: ${it.message}. Update the main installer/bridge first." }
        record(message)
        main.post {
            ui.testStart.isEnabled = true
            if (visible) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
    @Deprecated("Legacy vehicle picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 12 && resultCode == RESULT_OK) data?.data?.let { uri ->
            worker.post {
                val ok = runCatching { contentResolver.openOutputStream(uri)?.use { out ->
                    logFile.inputStream().use { it.copyTo(out) }
                } ?: error("No output") }.isSuccess
                main.post { Toast.makeText(this, if(ok) "Log saved" else "Save failed", Toast.LENGTH_LONG).show() }
            }
        }
    }
    private fun resolveFields() {
        val path = "/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk"
        check(File(path).isFile) { "Installed vehicle SDK not found" }
        val loader = dalvik.system.PathClassLoader(path, classLoader)
        val type = loader.loadClass("com.qinggan.canbus.VehicleState")
        fun identity(name: String): Pair<Int, Int> {
            val field = type.getField(name).get(null) as Enum<*>
            return field.ordinal to (type.getMethod("getValue").invoke(field) as Int)
        }
        dowIdentity = identity("DOW_STATE"); switchIdentity = identity("DOW_SYSTEM_SWITCH")
        record("SDK DOW=" + dowIdentity + " switch=" + switchIdentity)
    }
    private fun call(code: Int, body: Parcel.() -> Unit = {}): Int {
        val request = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR); request.body()
            check(binder?.transact(code, request, reply, 0) == true)
            reply.readException(); return reply.readInt()
        } finally { request.recycle(); reply.recycle() }
    }
    private fun read(ordinal: Int, id: Int) = call(0x39) { writeInt(1); writeInt(ordinal); writeInt(id) }
    private val poll = object : Runnable {
        override fun run() {
            if (!visible || binder == null) return
            try {
                state = checkNotNull(dowIdentity).let { read(it.first, it.second) }
                switch = checkNotNull(switchIdentity).let { read(it.first, it.second) }
                acc = call(0x42)
                val text = "SAMPLE state=" + state + " switch=" + switch + " acc=" + acc + " target=" + target + " match=" + (state == target)
                val now = SystemClock.elapsedRealtime()
                if (text != lastSample || now - lastHeartbeat >= 1000) { record(text); lastSample = text; lastHeartbeat = now }
                display(); worker.postDelayed(this, 500)
            } catch (e: Exception) { disconnect(e.toString()) }
        }
    }
    private fun disconnect(reason: String) {
        runCatching { call(0x1d) { writeStrongBinder(callback) } }
        binder = null; state = null; switch = null; acc = null
        record("DISCONNECTED " + reason); display()
    }
    private fun display() {
        val text = "DOW_STATE: " + (state ?: "?") + "     Switch: " + (switch ?: "?") + "     ACC: " + (acc ?: "?") + "     Target: " + target
        main.post { ui.status.text = text }
    }
    private val frame = object : Runnable {
        override fun run() {
            if (!visible) return
            val match = state != null && state == target
            val now = SystemClock.elapsedRealtime()
            if (match && !wasMatch) flashStarted = now
            wasMatch = match
            ui.warning.alpha = if (match) DowContract.level(now - flashStarted) / 10f else .12f
            main.postDelayed(this, 16)
        }
    }
    private fun record(message: String) {
        val line = System.currentTimeMillis().toString() + " uptime=" + SystemClock.elapsedRealtime() + " " + message
        Log.i("DOWProbe", line)
        runCatching {
            if (logFile.length() > 2_000_000) {
                val old = File(logFile.parentFile, "dow-probe.previous.log")
                check(logFile.renameTo(old)) { "Log rotation failed" }
            }
            logFile.appendText(line + "\n")
        }
        recent.addLast(line); while (recent.size > 35) recent.removeFirst()
        val text = recent.joinToString("\n")
        main.post { ui.logs.text = text }
    }
    companion object {
        private const val DESCRIPTOR = "com.qinggan.canbus.ICanBusService"
        private const val CALLBACK = "com.qinggan.canbus.ICanBusServiceCallback"
    }
}
