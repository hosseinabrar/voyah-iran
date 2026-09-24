package com.abrar.voyahiran.feature.navigation

/** Main-thread handoff from accessibility to the notification listener's one-second publisher.
 * A missing/hidden panel clears immediately; service death cannot preserve an old instruction. */
internal object NeshanVisibleGuidance {
    private var frame: ClusterNavigationFrame? = null
    private var readAt = 0L
    fun update(value: ClusterNavigationFrame?, now: Long) { frame = value; readAt = now }
    fun current(now: Long) = frame?.takeIf { now - readAt in 0..2500L }
    fun clear() { frame = null }
}
