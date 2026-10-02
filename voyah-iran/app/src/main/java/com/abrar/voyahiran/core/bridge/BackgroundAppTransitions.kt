package com.abrar.voyahiran.core.bridge

internal class BackgroundAppTransitions {
    private var previous: Set<String>? = null
    private val pending = mutableMapOf<String, Long>()
    fun reset() { previous = null; pending.clear() }
    fun sample(visible: Set<String>, now: Long): Set<String> {
        previous?.minus(visible)?.forEach { pending.putIfAbsent(it, now) }
        visible.forEach(pending::remove)
        previous = visible.toSet()
        val due = pending.filterValues { now - it >= 5000 }.keys.toSet()
        due.forEach(pending::remove)
        return due
    }
}
