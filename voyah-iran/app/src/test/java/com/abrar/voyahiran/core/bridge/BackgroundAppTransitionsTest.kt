package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class BackgroundAppTransitionsTest {
    @Test fun waitsAfterLeavingAndEmitsOnlyOnce() {
        val tracker = BackgroundAppTransitions()
        assertTrue(tracker.sample(setOf("music"), 0).isEmpty())
        assertTrue(tracker.sample(setOf("home"), 1000).isEmpty())
        assertTrue(tracker.sample(setOf("home"), 5999).isEmpty())
        assertEquals(setOf("music"), tracker.sample(setOf("home"), 6000))
        assertTrue(tracker.sample(setOf("home"), 9000).isEmpty())
    }
    @Test fun passengerVisibilityAndTransfersProtectApp() {
        val tracker = BackgroundAppTransitions()
        tracker.sample(setOf("music", "map"), 0)
        assertTrue(tracker.sample(setOf("map", "music"), 8000).isEmpty())
        tracker.sample(setOf("map"), 9000)
        tracker.sample(setOf("music", "map"), 10000)
        assertTrue(tracker.sample(setOf("music", "map"), 20000).isEmpty())
    }
    @Test fun disablingOrFailedSnapshotDiscardsOldCandidates() {
        val tracker = BackgroundAppTransitions()
        tracker.sample(setOf("music"), 0)
        tracker.sample(setOf("home"), 1000)
        tracker.reset()
        assertTrue(tracker.sample(setOf("home"), 20000).isEmpty())
    }
}
