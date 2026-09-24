package com.abrar.voyahiran.feature.navigation

import org.junit.Assert.*
import org.junit.Test

class NeshanEntrySessionTest {
    @Test fun initialEntryWaitsForMapToLoad() {
        val s = NeshanEntrySession()
        assertFalse(s.canAttempt(0))
        s.enter(100)
        assertTrue(s.canAttempt(100))
        assertTrue(s.canAttempt(19_000))
        assertFalse(s.canAttempt(20_100))
    }
    @Test fun manualExitOrContentChangesCannotRepeatClick() {
        val s = NeshanEntrySession()
        s.enter(0); s.consume()
        repeat(100) { s.enter(it.toLong()); assertFalse(s.canAttempt(it.toLong())) }
    }
    @Test fun leavingAndReopeningAllowsAnotherAttempt() {
        val s = NeshanEntrySession()
        s.enter(0); s.consume(); s.leave()
        assertFalse(s.canAttempt(100))
        s.enter(100)
        assertTrue(s.canAttempt(100))
    }
    @Test fun repeatedWindowEventsDoNotExtendDeadline() {
        val s = NeshanEntrySession()
        s.enter(0); s.enter(19_000)
        assertFalse(s.canAttempt(20_000))
    }
    @Test fun alreadyDrivingConsumesTheVisitToo() {
        val s = NeshanEntrySession()
        s.enter(0); s.consume()
        assertFalse(s.canAttempt(1000))
    }
}
