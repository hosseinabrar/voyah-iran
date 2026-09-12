package com.abrar.voyahiran.feature.rearcamera.domain

import kotlinx.coroutines.delay

data class RearCameraSnapshot(val dvrAvailable: Boolean, val cameraAvailable: Boolean, val status: Int?, val switchReady: Boolean = true) {
    val enabled get() = status == 2 && switchReady
    val canChange get() = dvrAvailable && cameraAvailable && status in 1..2
}

interface RearCameraPort {
    suspend fun read(): RearCameraSnapshot
    suspend fun configureSwitch(enabled: Boolean) {}
    suspend fun toggle()
}

/** ROA control is a toggle pulse, not an idempotent on/off setter. */
class RearCameraRepository(private val port: RearCameraPort, private val pause: suspend () -> Unit = { delay(500) }) {
    suspend fun read() = port.read()

    suspend fun setEnabled(enabled: Boolean): RearCameraSnapshot {
        val before = port.read()
        check(before.canChange) { "Rear camera unavailable" }
        // A pre-existing ACTIVE CAN state must not be toggled off when the UI hook is missing.
        val targetStatus = if (enabled) 2 else 1
        if (!enabled) port.configureSwitch(false)
        if (before.status != targetStatus) {
            port.toggle() // Never retry a pulse after an ambiguous response.
            var confirmed = false
            for (attempt in 0 until 20) {
                pause()
                val current = port.read()
                check(current.canChange) { "Rear camera unavailable" }
                if (current.status == targetStatus) { confirmed = true; break }
            }
            check(confirmed) { "Rear camera change not confirmed" }
        }
        if (enabled) port.configureSwitch(true)
        val actual = port.read()
        check(actual.status == targetStatus && actual.enabled == enabled) { "Rear camera UI change not confirmed" }
        return actual
    }
}
