package com.abrar.voyahiran.core.bridge

import java.util.concurrent.locks.ReentrantLock

/** Independent actuators must not reject each other's startup commands. */
internal class ComfortExecutionLocks {
    val dow = ReentrantLock()
    private val shade = ReentrantLock()
    private val aeb = ReentrantLock()
    private val pedestrian = ReentrantLock()

    fun forOperation(operation: String, fallback: ReentrantLock): ReentrantLock = when (operation) {
        "dow-light" -> dow
        "sunshade-ready", "sunshade-departure" -> shade
        "aeb-memory" -> aeb
        "pedestrian-memory" -> pedestrian
        else -> fallback // The three ordered powertrain memories retain their shared lane.
    }
}
