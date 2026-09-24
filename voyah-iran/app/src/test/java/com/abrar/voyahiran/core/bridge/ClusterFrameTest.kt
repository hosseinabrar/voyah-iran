package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class ClusterFrameTest {
    @Test fun unchangedDisplayDoesNotNeedResending() {
        val original = ClusterFrame("Hemmat", 7, 87).displayValue()
        assertEquals(original, ClusterFrame("Hemmat", 7, 86).displayValue())
        assertNotEquals(original, ClusterFrame("Hemmat", 7, 79).displayValue())
        assertNotEquals(original, ClusterFrame("Hemmat", 3, 87).displayValue())
        assertNotEquals(original, ClusterFrame("Modiriat", 7, 87).displayValue())
        assertNotEquals(original, ClusterFrame("Hemmat", 7, -1).displayValue())
    }
    @Test fun displayNormalizationIsIdempotent() {
        for (meters in listOf(-1, 0, 8, 9, 10, 11, 500, 501, 999, 1000, 8778, 100001)) {
            val frame = ClusterFrame("-", 0, meters).displayValue()
            assertEquals(frame, frame.displayValue())
        }
    }
}
