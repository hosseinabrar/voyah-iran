package com.abrar.voyahiran.core.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterDistanceTest {
    @Test fun requestedBoundaries() {
        for ((input, expected) in mapOf(0 to 0, 8 to 0, 9 to 0, 10 to 10, 11 to 10, 19 to 10, 20 to 20))
            assertEquals(expected, ClusterDistance.floorMeters(input))
    }
    @Test fun oemFormattingCannotRoundUpAgain() {
        for (raw in 0..1000000) {
            val value = ClusterDistance.floorMeters(raw)
            val oemMeters = if (value > 500) value - value % 50 else (value + 5) - (value + 5) % 10
            assertEquals(value, oemMeters)
            assertTrue(value <= raw)
            if (value >= 1000 && value < 100000) assertEquals(0, value % 100)
        }
    }
}
