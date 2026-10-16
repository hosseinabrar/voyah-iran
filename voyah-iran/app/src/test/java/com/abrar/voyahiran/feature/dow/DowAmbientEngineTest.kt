package com.abrar.voyahiran.feature.dow

import org.junit.Assert.*
import org.junit.Test

class DowAmbientEngineTest {
    private val original = AmbientSnapshot(1, 2, 1, 19, 4)
    private class Store(var journal: AmbientJournal? = null) : AmbientStore {
        var fail = false
        override fun read() = journal
        override fun write(value: AmbientJournal) { check(!fail); journal = value }
    }
    private class Port(var current: AmbientSnapshot) : AmbientPort {
        val calls = mutableListOf<String>()
        var confirm = true
        var fail = false
        var onNormal: () -> Unit = {}
        override fun snapshot() = current
        override fun normalRed() { onNormal(); calls += "normal-red"; check(!fail) }
        override fun level(value: Int) { calls += "level:" + value; check(!fail) }
        override fun restore(snapshot: AmbientSnapshot) { calls += "restore"; check(!fail); if (confirm) current = snapshot }
        override fun confirmed(snapshot: AmbientSnapshot) = confirm && current == snapshot
    }
    @Test fun experimentalValueIsNotFaultAndCurveIsBoundedAndPeriodic() {
        assertNotEquals(4, DowContract.EXPERIMENTAL_WARNING)
        assertEquals(1, DowContract.level(0)); assertEquals(10, DowContract.level(50))
        assertEquals(5, DowContract.level(250)); assertEquals(1, DowContract.level(450))
        for (t in 0L..2000) {
            assertTrue(DowContract.level(t) in 1..10)
            assertEquals(DowContract.level(t), DowContract.level(t + 500))
        }
    }
    @Test fun durablySavesBeforeFirstWriteAndOnlyOnceDuringWarning() {
        val store = Store(); val port = Port(original)
        port.onNormal = { assertEquals(AmbientJournal(original, true), store.journal) }
        val engine = DowAmbientEngine(port, store)
        for (t in 0L..2000 step 25) engine.tick(t, true, 2, 3)
        assertTrue(engine.flashing); assertEquals(1, port.calls.count { it == "normal-red" })
        assertEquals(original, store.journal!!.snapshot); assertTrue(store.journal!!.pending)
        assertTrue(port.calls.contains("level:10")); assertTrue(port.calls.contains("level:1"))
        assertFalse(port.calls.contains("level:0"))
    }
    @Test fun anyStateChangeRestoresPowerModeColorAndBrightness() {
        for (next in listOf(null, 0, 1, 2, 4, 5, 6, 7)) {
            val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
            engine.tick(0, true, 2, 3); engine.tick(100, true, 2, next)
            assertFalse(engine.flashing); assertFalse(store.journal!!.pending)
            assertEquals(original, port.current); assertEquals("restore", port.calls.last())
        }
    }
    @Test fun disableAndVipExpiryRestoreWithoutErasingBaseline() {
        val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
        engine.tick(0, true, 2, 3); engine.tick(100, false, 2, 3)
        assertFalse(engine.flashing); assertEquals(AmbientJournal(original, false), store.journal)
    }
    @Test fun rebootDuringWarningRollsBackBeforeAnyNewWarningEvenWithoutVip() {
        val store = Store(AmbientJournal(original, true)); val port = Port(original.copy(power = 2, color = 62))
        DowAmbientEngine(port, store).tick(0, false, 2, 3)
        assertEquals(listOf("restore"), port.calls); assertFalse(store.journal!!.pending)
    }
    @Test fun normalHiddenMemoryRestoresAtNextActivation() {
        val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
        engine.tick(0, true, 2, 1); engine.tick(100, true, 0, 1)
        port.current = original.copy(color = 5)
        engine.tick(200, true, 2, 1)
        assertEquals(original, port.current); assertEquals("restore", port.calls.last())
    }
    @Test fun startupWithStoredNormalMemoryRestoresInsteadOfLearningFactoryReset() {
        val store = Store(AmbientJournal(original, false)); val port = Port(original.copy(color = 5))
        DowAmbientEngine(port, store).tick(0, true, 2, 1)
        assertEquals(original, port.current)
    }
    @Test fun unconfirmedRestoreRetainsJournalAndDoesNotRestartRed() {
        val store = Store(AmbientJournal(original, true)); val port = Port(original).apply { confirm = false }
        val engine = DowAmbientEngine(port, store)
        for (t in 0L..4999 step 25) engine.tick(t, true, 2, 3)
        assertTrue(store.journal!!.pending); assertEquals(listOf("restore"), port.calls)
        port.confirm = true; engine.tick(5000, true, 2, 3)
        assertFalse(store.journal!!.pending)
    }
    @Test fun lostConnectionNeverWritesAndReconnectRecovers() {
        val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
        engine.tick(0, true, 2, 3); engine.connectionLost()
        val count = port.calls.size
        engine.tick(100, true, null, null); assertEquals(count, port.calls.size)
        engine.tick(200, true, 2, 3); assertEquals("restore", port.calls.last())
    }
    @Test fun durableStorageFailurePreventsPhysicalWrites() {
        val store = Store().apply { fail = true }; val port = Port(original)
        try { DowAmbientEngine(port, store).tick(0, true, 2, 3); fail() } catch (_: IllegalStateException) {}
        assertTrue(port.calls.isEmpty())
    }
    @Test fun partialApplyFailureLeavesDurableRecovery() {
        val store = Store(); val port = Port(original).apply { fail = true }; val engine = DowAmbientEngine(port, store)
        try { engine.tick(0, true, 2, 3); fail() } catch (_: IllegalStateException) {}
        assertTrue(store.journal!!.pending); assertEquals(original, store.journal!!.snapshot)
        port.fail = false; engine.tick(100, true, 2, 3)
        assertFalse(store.journal!!.pending); assertFalse(engine.flashing)
        engine.tick(3000, true, 2, 3); assertFalse(engine.flashing)
    }
    @Test fun idleMemoryTracksUserChangesAndInvalidSnapshotCannotOverwriteIt() {
        val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
        engine.tick(0, true, 2, 1)
        port.current = original.copy(color = 5, power = 2); engine.tick(2000, true, 2, 1)
        assertEquals(5, store.journal!!.snapshot.color)
        port.current = original.copy(color = 255)
        try { engine.tick(4000, true, 2, 1); fail() } catch (_: IllegalStateException) {}
        assertEquals(5, store.journal!!.snapshot.color)
    }
    @Test fun warningContinuesAfterAccOffAndCanStartWhileExiting() {
        val store = Store(); val port = Port(original); val engine = DowAmbientEngine(port, store)
        engine.tick(0, true, 2, 3); engine.tick(100, true, 0, 3)
        assertTrue(engine.flashing); assertFalse(port.calls.contains("restore"))
        engine.tick(200, true, 0, 1); assertFalse(engine.flashing); assertEquals("restore", port.calls.last())
        val off = DowAmbientEngine(Port(original), Store())
        off.tick(0, true, 0, 3); assertTrue(off.flashing)
    }
    @Test fun noEntitlementOrUnknownPowerOrFaultNeverStartsWarning() {
        for ((allowed, acc, state) in listOf(Triple(false,2,3),Triple(true,2,4),Triple(true,null,3))) {
            val port = Port(original); val engine = DowAmbientEngine(port, Store())
            engine.tick(0, allowed, acc, state); assertFalse(engine.flashing); assertTrue(port.calls.isEmpty())
        }
    }
}
