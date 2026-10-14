package com.abrar.voyahiran.feature.launcher

enum class ClusterMapMode {
    OFF, AUTO, ON;
    fun effective(neshan: Boolean) = if (this == AUTO && !neshan) ON else this
    fun next(neshan: Boolean): ClusterMapMode = when (effective(neshan)) {
        OFF -> if (neshan) AUTO else ON
        AUTO -> ON
        ON -> OFF
    }
}

/** Monotonic-clock policy; no UI, preference writes or vehicle commands. */
internal class ClusterMapAutoPolicy {
    private var step: String? = null
    private var visible = false
    private var near = false
    private var hideAt: Long? = null
    private var freshUntil = 0L

    fun reset() { step = null; visible = false; near = false; hideAt = null; freshUntil = 0 }

    fun update(key: String?, meters: Int, sampledAt: Long, now: Long) {
        if (key.isNullOrBlank() || meters !in 0..1_000_000 || now - sampledAt !in 0..5000) {
            reset(); return
        }
        // Never resurrect a previous maneuver after a broken/stale connection.
        if (now > freshUntil) reset()
        if (step != null && step != key && visible) hideAt = now + 4000
        step = key
        freshUntil = sampledAt + 5000
        near = meters < 200
        if (!visible && near) visible = true
        // Once triggered, distance jitter must not hide the same maneuver.
        show(now)
    }

    fun show(now: Long): Boolean {
        if (now > freshUntil) { reset(); return false }
        if (hideAt?.let { now >= it } == true) {
            visible = near // Keep the map for immediately consecutive nearby maneuvers.
            hideAt = null
        }
        return visible
    }
}
