package com.abrar.voyahiran.feature.drivememory.domain

/** Close on observed READY 1->0, without waiting for ACC power-off.
 * No replay after cold startup/reconnect; every edge is consumed, including unsafe ones. */
class SunshadeDeparturePolicy {
    private var previous: Int? = null
    fun reset(ready: Int? = null) { previous = ready?.takeIf { it in 0..1 } }
    fun observe(ready: Int?, speed: Int?, enabled: Boolean, vip: Boolean): Boolean {
        if (!enabled || !vip) { reset(); return false }
        val leaving = previous == 1 && ready == 0
        reset(ready)
        return leaving && speed == 0
    }
}
