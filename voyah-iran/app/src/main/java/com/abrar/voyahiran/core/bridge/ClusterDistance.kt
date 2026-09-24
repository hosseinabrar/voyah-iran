package com.abrar.voyahiran.core.bridge

/** Pre-quantize to a value the OEM GuidanceFormat can display without rounding up. */
internal object ClusterDistance {
    fun floorMeters(meters: Int): Int {
        require(meters >= 0)
        val step = when {
            meters <= 500 -> 10
            meters < 1000 -> 50
            meters < 100000 -> 100 // OEM shows one decimal digit in kilometres.
            else -> 1000
        }
        return meters - meters % step
    }
}
