package com.abrar.voyahiran.feature.drivememory.domain

/** Open only after observing OFF/not-ready followed by powered READY.
 * Startup/reconnect while already READY does not move the shade. */
class SunshadeReadyPolicy {
    private var armed = false
    fun reset(ready: Int? = null) { armed = ready == 0 }
    fun observe(acc: Int?, ready: Int?, speed: Int?, enabled: Boolean, vip: Boolean): Boolean {
        if (!enabled || !vip) { reset(); return false }
        if (acc == 0) { armed = true; return false }
        if (acc != 2 || ready !in 0..1) { reset(); return false }
        if (ready == 0) { armed = true; return false }
        val entered = armed
        armed = false
        return entered && speed == 0
    }
}
