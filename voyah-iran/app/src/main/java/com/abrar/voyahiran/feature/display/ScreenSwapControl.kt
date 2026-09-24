package com.abrar.voyahiran.feature.display

import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares the package's existing key interceptor with star, voice and media. */
class ScreenSwapControl(private val app: VoyahIranApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Mutex()
    private val gate = ScreenSwapPress()
    private val runner = ProcessCommandRunner()
    @Volatile private var readyUntil = 0L
    private fun enabled() = app.subscriptions.allowed &&
        Settings.Global.getInt(app.contentResolver, "voyah_iran_screen_swap", 1) == 1
    fun start() { scope.launch {
        while (isActive) {
            try {
                if (enabled()) {
                    operations.withLock {
                        val result = runner.run(listOf("su", "0", "screen-swap-runtime", "prepare"), 3)
                        readyUntil = if (result.exitCode == 0 && !result.timedOut && result.output.trim() in listOf("READY", "EMPTY"))
                            SystemClock.elapsedRealtime() + 1200 else 0
                        if (result.exitCode != 0 || result.timedOut)
                            AppLog.changed("ScreenSwap", "prepare failed=${result.exitCode} ${result.output}")
                    }
                    app.steeringKeys.setScreenSwapListener(::key)
                } else {
                    readyUntil = 0; gate.reset()
                    app.steeringKeys.setScreenSwapListener(null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                readyUntil = 0
                AppLog.changed("ScreenSwap", "key registration/prepare failed: ${e.message}")
            }
            delay(500)
        }
    } }
    private fun key(key: Int, action: Int): Boolean {
        if (key != 3115) return false
        val decision = gate.route(action, enabled() && SystemClock.elapsedRealtime() < readyUntil)
        AppLog.info("ScreenSwap", "key=$key action=$action consume=${decision.first} execute=${decision.second}")
        if (decision.second) {
            readyUntil = 0
            // Never call the bridge while holding the OEM key Binder callback.
            scope.launch {
                if (!enabled()) return@launch
                operations.withLock {
                    val result = runner.run(listOf("su", "0", "screen-swap-runtime", "execute"), 8)
                    AppLog.info("ScreenSwap", "result=${result.exitCode} ${result.output}")
                    if (result.exitCode == 0 && result.output.trim() == "EMPTY") {
                        withContext(Dispatchers.Main) {
                            val display = app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0)
                            if (display != null) android.widget.Toast.makeText(app.createDisplayContext(display),
                                "No apps on this screen support switch", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        return decision.first
    }
}
