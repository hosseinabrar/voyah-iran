package com.abrar.voyahiran.core.bridge

internal data class ClusterFrame(val text: String, val icon: Int, val meters: Int) {
    fun displayValue() = copy(meters = if (meters < 0) -1 else ClusterDistance.floorMeters(meters))
}
