package com.abrar.voyahiran.feature.voice

/** Mirrors FragmentDrivePreferenceBindingImpl, including its relabelled Fuel/Save buttons. */
object EnergyStrategyPolicy {
    fun modes(extends: Boolean, caPlatform: Boolean, xOverseas: Boolean): Map<String, Int> {
        if (!extends) return emptyMap()
        return buildMap {
            put("electric", 2)
            if (caPlatform || xOverseas) put("hybrid", 3) else {
                put("smart", 1)
                put("fuel", 3)
            }
            if (xOverseas) put("fuel", 4) else put("save", 4)
        }
    }
    fun reserve(percent: Int): Int {
        require(percent in 25..80 && (percent - 25) % 5 == 0) { "Reserve must match the OEM 25..80 / 5 slider" }
        return (percent - 25) / 5
    }
}
