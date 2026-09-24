package com.abrar.voyahiran.feature.media

import org.junit.Assert.*
import org.junit.Test

class MusicAutomationPolicyTest {
    @Test fun initialOpenDoesNotPauseNewPlayback() {
        val policy = MusicAutomationPolicy()
        assertFalse(policy.doorOpened(1))
        assertFalse(policy.doorOpened(1))
    }
    @Test fun eachOpeningPausesExactlyOnce() {
        val policy = MusicAutomationPolicy()
        assertFalse(policy.doorOpened(0))
        assertTrue(policy.doorOpened(1))
        assertFalse(policy.doorOpened(1))
        assertFalse(policy.doorOpened(0))
        assertTrue(policy.doorOpened(1))
    }
    @Test fun unknownDataIsNeverTreatedAsDoorOpening() {
        val policy = MusicAutomationPolicy()
        assertFalse(policy.doorOpened(0))
        assertFalse(policy.doorOpened(-1))
        assertFalse(policy.doorOpened(1))
        assertFalse(policy.doorOpened(255))
    }
    @Test fun disablingOrReconnectingResetsDoorBaseline() {
        val policy = MusicAutomationPolicy()
        policy.doorOpened(0)
        policy.resetDoor()
        assertFalse(policy.doorOpened(1))
    }
}
