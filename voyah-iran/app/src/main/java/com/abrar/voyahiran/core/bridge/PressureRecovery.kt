package com.abrar.voyahiran.core.bridge

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Only missing pressure is polled; a valid callback cancels recovery immediately. */
internal class PressureRecovery(
    private val executor: ScheduledExecutorService,
    private val read: () -> Int?,
    private val recovered: (Int) -> Unit
) {
    private var pending: ScheduledFuture<*>? = null
    private var generation = 0L
    private var closed = false
    @Synchronized fun observed(value: Int?) {
        // Repeated missing-value callbacks must not postpone the five-second deadline.
        if (!valid(value) && pending != null) return
        generation++
        pending?.cancel(false)
        pending = null
        if (!closed && !valid(value)) schedule()
    }
    private fun schedule() {
        val expected = generation
        pending = executor.schedule({
            val value = runCatching(read).getOrNull()
            synchronized(this) {
                // A callback or close while the read was in flight wins.
                if (!closed && generation == expected) {
                    pending = null
                    if (valid(value)) recovered(value!!) else schedule()
                }
            }
        }, 5, TimeUnit.SECONDS)
    }
    @Synchronized fun close() {
        closed = true
        generation++
        pending?.cancel(false)
        pending = null
    }
    companion object {
        // Same validity rule as the card: null, zero and negative readings are missing.
        fun valid(value: Int?) = value != null && value > 0
    }
}
