package com.abrar.voyahiran.feature.rearcamera

import com.abrar.voyahiran.feature.rearcamera.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RearCameraRepositoryTest {
    private class Port(var snapshot: RearCameraSnapshot = RearCameraSnapshot(true, true, 1)) : RearCameraPort {
        var switchWrites = 0
        override suspend fun configureSwitch(enabled: Boolean) { switchWrites++; snapshot = snapshot.copy(switchReady = enabled) }
        var writes = 0
        var acknowledge = true
        var fail = false
        override suspend fun read() = snapshot
        override suspend fun toggle() {
            writes++
            if (fail) error("ambiguous transport")
            if (acknowledge) snapshot = snapshot.copy(status = if (snapshot.status == 2) 1 else 2)
        }
    }
    @Test fun activeCanWithoutHookEnablesOnlyTheHook() = runBlocking {
        val port = Port(RearCameraSnapshot(true, true, 2, false))
        assertTrue(RearCameraRepository(port) {}.setEnabled(true).enabled)
        assertEquals(0, port.writes)
        assertEquals(1, port.switchWrites)
    }
    @Test fun disableRemovesHookAndThenSendsOnePulse() = runBlocking {
        val port = Port(RearCameraSnapshot(true, true, 2, true))
        assertFalse(RearCameraRepository(port) {}.setEnabled(false).enabled)
        assertFalse(port.snapshot.switchReady)
        assertEquals(1, port.writes)
    }
    @Test fun confirmsBothDirections() = runBlocking {
        val port = Port(); val repo = RearCameraRepository(port) {}
        assertTrue(repo.setEnabled(true).enabled)
        assertFalse(repo.setEnabled(false).enabled)
        assertEquals(2, port.writes)
    }
    @Test fun alreadyCorrectNeverToggles() = runBlocking {
        val port = Port(); val repo = RearCameraRepository(port) {}
        repo.setEnabled(false); assertEquals(0, port.writes)
    }
    @Test fun unavailableDvrNeverWrites() = runBlocking {
        val port = Port(RearCameraSnapshot(false, true, 1))
        assertTrue(runCatching { RearCameraRepository(port) {}.setEnabled(true) }.isFailure)
        assertEquals(0, port.writes)
    }
    @Test fun unknownOrFaultedStatusNeverWrites() = runBlocking {
        for (status in listOf(null, 0, 3, 255)) {
            val port = Port(RearCameraSnapshot(true, true, status))
            assertTrue(runCatching { RearCameraRepository(port) {}.setEnabled(true) }.isFailure)
            assertEquals(0, port.writes)
        }
    }
    @Test fun missingRearHardwareNeverWrites() = runBlocking {
        val port = Port(RearCameraSnapshot(true, false, 1))
        assertTrue(runCatching { RearCameraRepository(port) {}.setEnabled(true) }.isFailure)
        assertEquals(0, port.writes)
    }
    @Test fun absentFeedbackNeverRetriesToggle() = runBlocking {
        val port = Port().apply { acknowledge = false }
        assertTrue(runCatching { RearCameraRepository(port) {}.setEnabled(true) }.isFailure)
        assertEquals(1, port.writes)
        assertFalse(port.snapshot.enabled)
    }
    @Test fun transportFailureNeverRetriesToggle() = runBlocking {
        val port = Port().apply { fail = true }
        assertTrue(runCatching { RearCameraRepository(port) {}.setEnabled(true) }.isFailure)
        assertEquals(1, port.writes)
    }
}

