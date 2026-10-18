package com.abrar.voyahiran.feature.drivememory

import com.abrar.voyahiran.core.bridge.BridgePolicy
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.drivememory.data.AebMemoryWriter
import com.abrar.voyahiran.feature.drivememory.domain.*
import org.junit.Assert.*
import org.junit.Test

class AebMemoryTest {
    @Test fun allThreeMemoriesRequireVip() {
        val ordinary = MemoryAccess(true, false)
        assertTrue(MemoryAccess(true, true).active(aeb = true, drive = false, recovery = false))
        assertFalse(ordinary.permits())
        assertFalse(ordinary.active(aeb = true, drive = false, recovery = false))
        assertFalse(ordinary.active(aeb = false, drive = true, recovery = true))
        assertTrue(MemoryAccess(true, true).active(aeb = false, drive = true, recovery = false))
        assertFalse(MemoryAccess(false, true).active(aeb = true, drive = true, recovery = true))
    }
    private class Store(var value: DriveMemory = DriveMemory()) : DriveMemoryStore {
        override fun read() = value
        override fun write(value: DriveMemory) { this.value = value }
    }
    private fun sample(value: Int) = VehicleSample(mode = value, acc = 2, speed = 0)

    @Test fun enablingCapturesBothStatesWithoutChangingVehicle() {
        for (value in 1..2) {
            val engine = DriveMemoryEngine(Store(), 1..2)
            engine.enable(sample(value), "boot")
            assertEquals(value, engine.memory.mode)
            assertNull(engine.observe(sample(value), 0, true))
            engine.disable()
            assertNull(engine.observe(sample(3 - value), 1, true))
        }
    }
    @Test fun restartRestoresBothStatesExactlyOnceAndWaitsForRealFeedback() {
        for (value in 1..2) {
            val engine = DriveMemoryEngine(Store(DriveMemory(true, value)), 1..2)
            engine.start("boot")
            assertEquals(value, engine.observe(sample(3 - value), 0, true))
            assertNull(engine.observe(sample(3 - value), 100, true))
            assertEquals(value, engine.memory.mode)
            assertTrue(engine.memory.pending)
            engine.observe(sample(value), 200, true)
            assertFalse(engine.memory.pending)
            assertEquals(DrivePhase.MONITORING, engine.phase)
        }
    }
    @Test fun unknownAebWaitsButSpeedDoesNotBlockRestore() {
        val engine = DriveMemoryEngine(Store(DriveMemory(true, 2)), 1..2)
        engine.start("boot")
        for (speed in listOf(null, 1, 50)) {
            val moving = DriveMemoryEngine(Store(DriveMemory(true, 2)), 1..2)
            moving.start("boot")
            assertEquals(2,moving.observe(sample(1).copy(speed=speed),0,true))
        }
        for (value in listOf(null, 0, 3, 255)) assertNull(engine.observe(sample(1).copy(mode = value), 0, true))
        assertNull(engine.observe(sample(1), 0, false))
        assertEquals(2, engine.observe(sample(1), 0, true))
    }
    @Test fun changesInFactoryMenuAreRememberedAndSurviveAccCycle() {
        val engine = DriveMemoryEngine(Store(), 1..2)
        engine.enable(sample(1), "boot")
        engine.observe(sample(2), 0, true)
        engine.observe(sample(2), 401, true)
        assertEquals(2, engine.memory.mode)
        engine.observe(sample(1).copy(acc = 0), 500, true)
        assertEquals(2, engine.memory.mode)
        assertEquals(2, engine.observe(sample(1), 600, true))
    }
    @Test fun failedRestoreDoesNotReplayUntilExplicitRetry() {
        val engine = DriveMemoryEngine(Store(DriveMemory(true, 2)), 1..2)
        engine.start("boot")
        assertEquals(2, engine.observe(sample(1), 0, true))
        engine.requestFailed()
        assertNull(engine.observe(sample(1), 20000, true))
        engine.retry()
        assertEquals(2, engine.observe(sample(1), 20001, true))
    }
    @Test fun writerAndBridgeAcceptOnlyTwoAebValues() {
        for (value in 1..2) {
            val command = listOf("aeb-memory", value.toString())
            assertEquals(command, BridgePolicy.resolve(command))
            AebMemoryWriter(CommandRunner { args, _ ->
                assertEquals(listOf("su", "0") + command, args)
                CommandResult(0, "AEB_CONFIRMED")
            }).apply(value)
        }
        for (value in listOf(-1, 0, 3, 255)) {
            assertNull(BridgePolicy.resolve(listOf("aeb-memory", "$value")))
            try { AebMemoryWriter(CommandRunner { _, _ -> error("Must not send") }).apply(value); fail() }
            catch (_: IllegalArgumentException) { }
        }
        assertNull(BridgePolicy.resolve(listOf("aeb-memory", "1", "extra")))
    }
    @Test fun deniedTimedOutOrUnconfirmedWritesAreFailures() {
        for (result in listOf(CommandResult(126, "Subscription inactive"), CommandResult(0, ""),
            CommandResult(0, "AEB_CONFIRMED", true), CommandResult(1, "AEB_CONFIRMED"))) {
            try { AebMemoryWriter(CommandRunner { _, _ -> result }).apply(2); fail() }
            catch (_: IllegalStateException) { }
        }
    }
}
