package com.abrar.voyahiran.feature.recoverymemory

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.drivememory.domain.*
import com.abrar.voyahiran.feature.recoverymemory.data.RecoveryLevelWriter
import com.abrar.voyahiran.feature.recoverymemory.domain.RecoveryRestoreOrder
import org.junit.Assert.*
import org.junit.Test

class RecoveryMemoryTest {
    private class Store(var value: DriveMemory = DriveMemory()) : DriveMemoryStore {
        override fun read() = value
        override fun write(value: DriveMemory) { this.value = value }
    }
    // Exercise restoration and drive/recovery ordering with electronics on, before READY.
    private fun sample(level: Int) = VehicleSample(level, 2, 0, 0)

    @Test fun allThreeLevelsCanBeCapturedWithoutApplyingAnything() {
        for (level in 2..4) {
            val store = Store(); val engine = DriveMemoryEngine(store, 2..4)
            engine.enable(sample(level), "boot")
            assertEquals(level, store.value.mode)
            assertNull(engine.observe(sample(level), 0, true))
        }
    }

    @Test fun startupStrongCannotEraseSavedWeak() {
        val store = Store(DriveMemory(true, 2, "old", false))
        val engine = DriveMemoryEngine(store, 2..4); engine.start("new")
        assertEquals(2, engine.observe(sample(4), 0, true))
        engine.observe(sample(4), 100, true)
        assertEquals(2, store.value.mode)
        assertTrue(store.value.pending)
        engine.observe(sample(2), 200, true)
        assertFalse(store.value.pending)
    }

    @Test fun levelsOutsideRecoveryRangeAreNotAccepted() {
        for (level in listOf(0, 1, 5, 6, 255)) {
            val engine = DriveMemoryEngine(Store(), 2..4)
            try { engine.enable(sample(level), "boot"); fail() } catch (_: IllegalStateException) { }
            try { RecoveryLevelWriter.arguments(level); fail() } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun modeRestoresBeforeRecoveryAndStrongFeedbackIsNotSavedDuringWait() {
        val driveStore = Store(DriveMemory(true, 2, "old", false))
        val energyStore = Store(DriveMemory(true, 3, "old", false))
        val drive = DriveMemoryEngine(driveStore); val energy = DriveMemoryEngine(energyStore, 2..4)
        val order = RecoveryRestoreOrder()
        drive.start("new"); energy.start("new")
        assertEquals(2, drive.observe(sample(1), 0, true))
        assertFalse(order.allow(true, true, false, false, 0))
        assertNull(drive.observe(sample(2), 100, true))
        assertFalse(order.allow(true, true, true, false, 100))
        assertFalse(order.allow(true, false, true, false, 1099))
        assertEquals(3, energyStore.value.mode)
        assertTrue(order.allow(true, false, true, false, 1100))
        assertEquals(3, energy.observe(sample(4), 1100, true))
    }

    @Test fun recoveryCanRunAloneAndDriveFailureDoesNotReleaseOrdering() {
        val order = RecoveryRestoreOrder()
        assertTrue(order.allow(false, false, false, false, 0))
        assertFalse(order.allow(true, true, false, false, 100))
        assertFalse(order.allow(true, true, false, false, 20000))
        assertTrue(order.allow(false, false, false, false, 20001))
    }

    @Test fun powerOffStillReachesEnergyEngineWhileDrivePending() {
        val order = RecoveryRestoreOrder()
        assertTrue(order.allow(true, true, false, true, 0))
        val store = Store(DriveMemory(true, 3, "boot", false))
        val engine = DriveMemoryEngine(store, 2..4)
        engine.observe(sample(4).copy(acc = 0), 0, true)
        assertEquals(3, store.value.mode)
        assertTrue(store.value.pending)
    }

    @Test fun independentlyDisablingOneMemoryDoesNotDisableTheOther() {
        val driveStore = Store(DriveMemory(true, 2, "boot", false))
        val energyStore = Store(DriveMemory(true, 3, "boot", false))
        val drive = DriveMemoryEngine(driveStore); val energy = DriveMemoryEngine(energyStore, 2..4)
        drive.disable()
        assertTrue(energy.memory.enabled)
        assertEquals(3, energy.memory.mode)
        energy.disable()
        assertFalse(energy.memory.enabled)
        assertEquals(2, driveStore.value.mode)
    }

    @Test fun restoredLevelTracksSubsequentSelection() {
        val store = Store(); val engine = DriveMemoryEngine(store, 2..4)
        engine.enable(sample(2), "boot")
        engine.observe(sample(3), 0, true)
        engine.observe(sample(3), 401, false)
        assertEquals(3, store.value.mode)
    }

    @Test fun binderCommandTargetsOnlyEnergySetState() {
        assertEquals(listOf("su", "0", "service", "call", "qg.canbus", "58",
            "i32", "1", "i32", "401", "i32", "619", "i32", "3"), RecoveryLevelWriter.arguments(3))
    }

    @Test fun successfulBinderReplyIsRecognized() {
        for (output in listOf("Result: Parcel(00000000 '....')", "Result: Parcel(\n  0x00000000: 00000000 '....')")) {
            RecoveryLevelWriter(CommandRunner { _, _ -> CommandResult(0, output) }).apply(2)
        }
    }

    @Test fun binderExceptionAndMissingServiceAreNotSuccess() {
        for (output in listOf("Result: Parcel(ffffffff '....')", "Service qg.canbus does not exist", "")) {
            val writer = RecoveryLevelWriter(CommandRunner { _, _ -> CommandResult(0, output) })
            try { writer.apply(3); fail() } catch (_: IllegalStateException) { }
        }
        val timed = RecoveryLevelWriter(CommandRunner { _, _ -> CommandResult(0, "Result: Parcel(00000000)", true) })
        try { timed.apply(3); fail() } catch (_: IllegalStateException) { }
    }
}
