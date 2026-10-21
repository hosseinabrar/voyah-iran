package com.abrar.voyahiran.feature.launcher

enum class ClusterMapMode {
    OFF, AUTO, ON;
    internal fun showsMap(nearManeuver: Boolean, mediaHasContent: Boolean?) = when (this) {
        OFF -> false
        ON -> true
        AUTO -> nearManeuver || mediaHasContent == false
    }
    fun effective(neshan: Boolean) = if (this == AUTO && !neshan) ON else this
    fun next(neshan: Boolean): ClusterMapMode = when (effective(neshan)) {
        OFF -> ON
        ON -> if (neshan) AUTO else OFF
        AUTO -> OFF
    }
}

/** Monotonic-clock policy; no UI, preference writes or vehicle commands. */
internal class ClusterMapAutoPolicy(private val log: (String) -> Unit = {}) {
    companion object {
        // Speed-adaptive lead time, bounded for slow traffic and fast roads.
        // Missing/invalid CAN speed preserves the previous 200 m behavior.
        internal fun triggerMeters(speedKmh: Int?): Double {
            val speed = speedKmh?.takeIf { it in 0..300 } ?: return 200.0
            val seconds = when {
                speed <= 50 -> 15
                speed <= 70 -> 20
                speed <= 90 -> 25
                speed <= 110 -> 30
                else -> 45
            }
            // Multiply before dividing to preserve exact integer distance boundaries.
            return (speed * seconds * 5.0 / 18.0).coerceIn(100.0, 1500.0)
        }
    }
    private var step: String? = null
    private var visible = false
    private var near = false
    private var hideAt: Long? = null
    private var freshUntil = 0L

    fun reset(reason: String = "owner_reset") {
        if (visible) log("exit reason=$reason step=$step freshUntil=$freshUntil hideAt=$hideAt")
        step = null; visible = false; near = false; hideAt = null; freshUntil = 0
    }

    // A failed read is not a navigation cancellation. Never extend the data lease.
    fun unavailable(now: Long) { show(now) }

    fun update(key: String?, meters: Int, sampledAt: Long, now: Long, speedKmh: Int? = null) {
        if (key.isNullOrBlank() || meters !in 0..1_000_000 || now - sampledAt !in 0..5000) {
            unavailable(now); return
        }
        // Never resurrect a previous maneuver after a broken/stale connection.
        if (now > freshUntil) reset("sample_expired now=$now")
        if (step != null && step != key && visible) hideAt = now + 4000
        step = key
        freshUntil = sampledAt + 5000
        near = meters < triggerMeters(speedKmh)
        if (!visible && near) visible = true
        // Once triggered, distance jitter must not hide the same maneuver.
        show(now)
    }

    fun show(now: Long): Boolean {
        if (now > freshUntil) { reset("sample_expired now=$now"); return false }
        if (hideAt?.let { now >= it } == true) {
            if (visible && !near) log("exit reason=maneuver_grace_elapsed step=$step now=$now")
            visible = near // Keep the map for immediately consecutive nearby maneuvers.
            hideAt = null
        }
        return visible
    }
}
