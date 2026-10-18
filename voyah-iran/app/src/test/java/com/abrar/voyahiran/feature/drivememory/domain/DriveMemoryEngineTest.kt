package com.abrar.voyahiran.feature.drivememory.domain

import org.junit.Assert.*
import org.junit.Test

class DriveMemoryEngineTest {
    private class Store(var value: DriveMemory = DriveMemory()) : DriveMemoryStore {
        var fail = false
        override fun read() = value
        override fun write(value: DriveMemory) { check(!fail); this.value = value }
    }
    private fun vehicle(mode: Int = 2) = VehicleSample(mode, 2, 1, 0)

    @Test fun savesAndTracksWithDisplayOnBeforeReady() {
        val store = Store(); val engine = DriveMemoryEngine(store)
        engine.enable(vehicle(2).copy(ready = 0), "boot")
        assertTrue(store.value.enabled)
        assertEquals(2, store.value.mode)
        assertNull(engine.observe(vehicle(3).copy(ready = 0), 0, true))
        assertNull(engine.observe(vehicle(3).copy(ready = 0), 500, true))
        assertEquals(3, store.value.mode)
    }

    @Test fun h97PowerAndReadyHaveSeparateMeanings() {
        assertTrue(vehicle().copy(ready = 0).powered)
        assertFalse(vehicle().copy(ready = 0).off)
        assertFalse(vehicle().copy(acc = 1).powered)
    }
    private fun enabledStore() = Store(DriveMemory(true, 2, "old", false))

    @Test fun enablingSavesCurrentModeWithoutRequestingAChange() {
        val store = Store(); val engine = DriveMemoryEngine(store)
        engine.enable(vehicle(6), "boot")
        assertEquals(6, store.value.mode)
        assertNull(engine.observe(vehicle(6), 0, true))
        assertFalse(store.value.pending)
    }

    @Test fun startupEcoDoesNotOverwriteSavedComfort() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new")
        assertEquals(2, engine.observe(vehicle(1), 0, true))
        assertNull(engine.observe(vehicle(1), 100, true))
        assertEquals(2, store.value.mode)
        assertTrue(store.value.pending)
    }

    @Test fun confirmedRestoreThenUserChangesAreRemembered() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new"); engine.observe(vehicle(1), 0, true)
        engine.observe(vehicle(2), 100, true)
        assertFalse(store.value.pending)
        engine.observe(vehicle(3), 200, true)
        engine.observe(vehicle(3), 601, false)
        assertEquals(3, store.value.mode)
    }

    @Test fun noWriteWhenAlreadyInSavedMode() {
        val engine = DriveMemoryEngine(enabledStore()); engine.start("new")
        assertNull(engine.observe(vehicle(), 0, true))
        assertEquals(DrivePhase.MONITORING, engine.phase)
    }

    @Test fun restorationDoesNotDependOnSpeed() {
        for(speed in listOf(0,30,null,Int.MIN_VALUE)) {
            val engine = DriveMemoryEngine(enabledStore()); engine.start("new")
            assertEquals(2,engine.observe(vehicle(1).copy(speed=speed),0,true))
        }
    }

    @Test fun waitsForCanButRestoresBeforeReady() {
        val engine = DriveMemoryEngine(enabledStore()); engine.start("new")
        assertNull(engine.observe(VehicleSample(), 0, true))
        assertNull(engine.observe(vehicle(1).copy(acc = 1, ready = 0), 1, true))
        assertNull(engine.observe(vehicle(0), 2, true))
        assertEquals(2, engine.observe(vehicle(1).copy(ready = 0), 3, true))
    }

    @Test fun unknownReadyDoesNotDelayRestoreOrEraseSavedMode() {
        val store = Store(DriveMemory(true, 3, "old", false))
        val engine = DriveMemoryEngine(store); engine.start("new")
        assertEquals(3, engine.observe(vehicle(1).copy(ready = null), 0, true))
        assertNull(engine.observe(vehicle(1).copy(ready = 0), 500, true))
        assertEquals(3, store.value.mode)
        assertTrue(store.value.pending)
        assertNull(engine.observe(vehicle(3).copy(ready = 0), 600, true))
        assertFalse(store.value.pending)
    }

    @Test fun accCycleRestoresWithoutAndroidReboot() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("same"); engine.observe(vehicle(), 0, true)
        engine.observe(vehicle(1).copy(acc = 0), 100, true)
        assertEquals(2, store.value.mode)
        assertEquals(2, engine.observe(vehicle(1), 200, true))
    }

    @Test fun lateShutdownDefaultDoesNotEraseMemory() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("same"); engine.observe(vehicle(), 0, true)
        engine.observe(vehicle(1), 10, true)
        engine.observe(vehicle(1).copy(acc = 0), 100, false)
        assertEquals(2, store.value.mode)
    }

    @Test fun processRestartWithSameBootStillGuardsAgainstMissedPowerCycle() {
        val engine = DriveMemoryEngine(enabledStore()); engine.start("old")
        assertEquals(2, engine.observe(vehicle(1), 0, true))
    }

    @Test fun failedConfirmationDoesNotOverwriteMemoryOrKeepSending() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new"); assertEquals(2, engine.observe(vehicle(1), 0, true))
        assertNull(engine.observe(vehicle(1), 10_001, true))
        assertEquals(DrivePhase.FAILED, engine.phase)
        assertNull(engine.observe(vehicle(1), 20_000, true))
        assertEquals(2, store.value.mode)
    }

    @Test fun explicitRetryCanResendOnce() {
        val engine = DriveMemoryEngine(enabledStore()); engine.start("new")
        engine.observe(vehicle(1), 0, true); engine.requestFailed()
        assertNull(engine.observe(vehicle(1), 20, true))
        engine.retry()
        assertEquals(2, engine.observe(vehicle(1), 21, true))
    }

    @Test fun disablingStopsSavingAndApplyingWithoutChangingLiveMode() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.disable()
        assertNull(engine.observe(vehicle(6), 2000, true))
        assertFalse(store.value.enabled)
        assertEquals(2, store.value.mode)
    }

    @Test fun invalidCurrentCannotEnableMemory() {
        val store = Store(); val engine = DriveMemoryEngine(store)
        try { engine.enable(vehicle(99), "boot"); fail() } catch (_: IllegalStateException) { }
        assertFalse(store.value.enabled)
    }

    @Test fun storageFailureDoesNotClaimSuccess() {
        val store = Store().apply { fail = true }; val engine = DriveMemoryEngine(store)
        try { engine.enable(vehicle(), "boot"); fail() } catch (_: IllegalStateException) { }
        assertFalse(engine.memory.enabled)
    }

    @Test fun corruptSavedModeNeverBecomesACommand() {
        val engine = DriveMemoryEngine(Store(DriveMemory(true, 99, "old", true)))
        engine.start("new")
        assertNull(engine.observe(vehicle(1), 0, true))
        assertEquals(DrivePhase.FAILED, engine.phase)
    }

    @Test fun revertedCandidateMustBeStableAgainBeforeSaving() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new"); engine.observe(vehicle(), 0, true)
        engine.observe(vehicle(3), 10, true); engine.observe(vehicle(), 100, true)
        engine.observe(vehicle(3), 1000, true)
        assertEquals(2, store.value.mode)
    }

    @Test fun powerSignalAloneCannotConfirmCachedModeAtStartup() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new")
        assertNull(engine.observe(vehicle(), 0, false))
        assertTrue(store.value.pending)
        assertEquals(2, engine.observe(vehicle(1), 1, true))
    }

    @Test fun unrelatedSignalCannotConfirmAnOutstandingRequest() {
        val store = enabledStore(); val engine = DriveMemoryEngine(store)
        engine.start("new"); engine.observe(vehicle(1), 0, true)
        engine.observe(vehicle(), 1, false)
        assertTrue(store.value.pending)
        engine.observe(vehicle(), 2, true)
        assertFalse(store.value.pending)
    }
}
