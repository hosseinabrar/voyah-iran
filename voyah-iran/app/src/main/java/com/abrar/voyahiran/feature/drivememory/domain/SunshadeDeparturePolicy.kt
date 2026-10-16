package com.abrar.voyahiran.feature.drivememory.domain

/** CAN ACC uses 2=on, 0=off on H97A/C/X (not CarSignalManager's separate ACC enum).
 * No replay after cold startup/reconnect; every off edge is consumed, including unsafe ones. */
class SunshadeDeparturePolicy {
    private var previous: Int? = null
    fun reset(acc: Int? = null) { previous = acc?.takeIf { it == 0 || it == 2 } }
    fun observe(acc: Int?, speed: Int?, enabled: Boolean, vip: Boolean): Boolean {
        if (!enabled || !vip) { reset(); return false }
        val leaving = previous == 2 && acc == 0
        reset(acc)
        return leaving && speed == 0
    }
}
