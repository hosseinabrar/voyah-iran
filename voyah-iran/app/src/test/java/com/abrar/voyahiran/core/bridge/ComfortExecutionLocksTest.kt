package com.abrar.voyahiran.core.bridge

import java.util.concurrent.locks.ReentrantLock
import org.junit.Assert.*
import org.junit.Test

class ComfortExecutionLocksTest {
    @Test fun independentCommandsDoNotShareMutationLocks() {
        val lanes = ComfortExecutionLocks()
        val fallback = ReentrantLock()
        val independent = listOf("dow-light", "sunshade-ready", "aeb-memory", "pedestrian-memory")
            .map { lanes.forOperation(it, fallback) }
        assertEquals(4, independent.toSet().size)
        assertFalse(independent.contains(fallback))
        assertSame(lanes.dow, independent.first())
        assertSame(lanes.forOperation("sunshade-ready", fallback), lanes.forOperation("sunshade-departure", fallback))
        assertSame(fallback, lanes.forOperation("energy-mode-memory", fallback))
        assertSame(fallback, lanes.forOperation("am", fallback))
        assertSame(fallback, lanes.forOperation("unknown", fallback))
    }
}
