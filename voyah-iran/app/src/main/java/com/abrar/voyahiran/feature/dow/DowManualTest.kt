package com.abrar.voyahiran.feature.dow

/** Independent of the automatic feature's heartbeat and preferences. */
class DowManualTest {
    private var until = 0L
    fun start(now: Long, vip: Boolean, acc: Int?, speed: Int?): Boolean {
        if (!vip || acc != 2 || speed != 0) return false
        until = now + 10_000
        return true
    }
    fun stop() { until = 0L }
    fun pending(now: Long, vip: Boolean): Boolean {
        if (!vip || now >= until) stop()
        return now < until
    }
    fun active(now: Long, vip: Boolean, acc: Int?, speed: Int?): Boolean {
        if (acc != 2 || speed != 0) stop()
        return pending(now, vip)
    }
}
