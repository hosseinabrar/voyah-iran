package com.abrar.voyahiran.feature.navigation

import org.junit.Assert.*
import org.junit.Test

class NeshanRouteProgressTest {
    @Test fun clicksAreOrderedAndGuidanceMustBeVerified() {
        val p = NeshanRouteProgress(1000)
        assertFalse(p.ready(1799))
        assertTrue(p.ready(1800))
        assertFalse(p.verify(true, 1800)) // Existing route is not proof that the new destination started.
        p.clicked(1800)
        assertEquals(NeshanRouteProgress.Step.START, p.step)
        assertFalse(p.verify(true, 2600))
        p.clicked(2600)
        assertFalse(p.verify(true, 3000))
        assertFalse(p.verify(false, 3400))
        assertTrue(p.verify(true, 3400))
    }
    @Test fun lateScreensCannotTriggerClicks() {
        val p = NeshanRouteProgress(0)
        assertFalse(p.ready(45000))
    }
}
