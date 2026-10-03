package com.abrar.raha

/** Relative PCM level, not calibrated acoustic decibels. Only used before wake-up. */
class RahaWakeVolumeGate {
    private var loudMs = 0
    private var eligibleUntil = Long.MIN_VALUE
    private var previousThreshold = -1
    fun accept(level: Int, threshold: Int, frameMs: Int, now: Long): Boolean {
        val floor = threshold.coerceIn(0, 100)
        if (floor != previousThreshold) { reset(); previousThreshold = floor }
        if (floor == 0) return true
        loudMs = if (level >= floor) (loudMs + frameMs).coerceAtMost(96) else 0
        // Require sustained energy, then allow decoder latency / pauses between syllables.
        if (loudMs >= 96) eligibleUntil = now + 600
        return now <= eligibleUntil
    }
    fun reset() { loudMs = 0; eligibleUntil = Long.MIN_VALUE }
}
