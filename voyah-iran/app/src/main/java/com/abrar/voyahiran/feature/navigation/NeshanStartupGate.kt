package com.abrar.voyahiran.feature.navigation

/** Require repeated, recent observations of real map controls, not just a splash window. */
internal class NeshanStartupGate {
    private var first: Long? = null
    private var last: Long? = null
    fun observe(mapVisible: Boolean, now: Long) {
        if (!mapVisible) { first = null; last = null; return }
        if (last == null || now - last!! > 1_000) first = now
        last = now
    }
    fun ready(now: Long): Boolean {
        val start = first ?: return false
        val end = last ?: return false
        return end - start >= 350 && now - end in 0..1_000
    }
}
