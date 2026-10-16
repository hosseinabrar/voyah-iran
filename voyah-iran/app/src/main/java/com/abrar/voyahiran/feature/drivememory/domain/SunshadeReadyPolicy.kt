package com.abrar.voyahiran.feature.drivememory.domain

/** Open only after observing READY 0->1 with power on.
 * Startup/reconnect while already READY does not move the shade. */
class SunshadeReadyPolicy {
    private var armed = false
    fun reset(ready: Int? = null) { armed = ready == 0 }
    fun observe(acc: Int?, ready: Int?, speed: Int?, enabled: Boolean, vip: Boolean): Boolean {
        if (!enabled || !vip) { reset(); return false }
        if (ready !in 0..1) { reset(); return false }
        if (ready == 0) { armed = true; return false }
        val entered = armed
        armed = false
        return entered && acc == 2 && speed == 0
    }
}
