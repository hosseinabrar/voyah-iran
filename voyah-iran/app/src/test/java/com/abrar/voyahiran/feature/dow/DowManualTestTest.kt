package com.abrar.voyahiran.feature.dow

import org.junit.Assert.*
import org.junit.Test

class DowManualTestTest {
    @Test fun expiresWithoutHeartbeatOrExplicitStop() {
        val test = DowManualTest()
        assertTrue(test.start(100, true, 2, 0))
        assertTrue(test.active(10099, true, 2, 0))
        assertFalse(test.active(10100, true, 2, 0))
    }
    @Test fun stopAndUnsafeStateEndLeaseWithoutResuming() {
        for ((vip, acc, speed) in listOf(Triple(false, 2, 0), Triple(true, 0, 0),
            Triple(true, 2, 1), Triple(true, 2, null), Triple(true, null, 0))) {
            val test = DowManualTest()
            assertFalse(test.start(0, vip, acc, speed))
            assertTrue(test.start(0, true, 2, 0))
            assertFalse(test.active(1, vip, acc, speed))
            assertFalse(test.active(2, true, 2, 0))
        }
        val test = DowManualTest()
        test.start(0, true, 2, 0); test.stop()
        assertFalse(test.pending(1, true))
    }
}
