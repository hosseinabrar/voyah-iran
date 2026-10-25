package com.abrar.voyahiran.feature.launcher

/** Retry failures without requiring another user action or spinning on a broken GPU. */
internal class ClusterMapRetry {
    private var failures = 0
    fun nextDelay(): Long {
        val delay = if (failures == 0) 3000L else 5000L
        failures = 1
        return delay
    }
    fun reset() { failures = 0 }
}
