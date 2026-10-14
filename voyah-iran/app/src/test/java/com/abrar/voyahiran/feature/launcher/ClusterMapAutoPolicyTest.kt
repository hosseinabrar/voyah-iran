package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMapAutoPolicyTest {
    @Test fun cyclesDependOnNavigatorWithoutChangingSavedAuto() {
        assertEquals(ClusterMapMode.AUTO, ClusterMapMode.OFF.next(true))
        assertEquals(ClusterMapMode.ON, ClusterMapMode.AUTO.next(true))
        assertEquals(ClusterMapMode.OFF, ClusterMapMode.ON.next(true))
        assertEquals(ClusterMapMode.ON, ClusterMapMode.OFF.next(false))
        assertEquals(ClusterMapMode.OFF, ClusterMapMode.ON.next(false))
        assertEquals(ClusterMapMode.ON, ClusterMapMode.AUTO.effective(false))
        assertEquals(ClusterMapMode.AUTO, ClusterMapMode.AUTO.effective(true))
    }
    @Test fun triggersStrictlyBelow200AndIgnoresDistanceJitter() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn1", 200, 1000, 1000); assertFalse(policy.show(1000))
        policy.update("turn1", 199, 1100, 1100); assertTrue(policy.show(1100))
        policy.update("turn1", 220, 1200, 1200); assertTrue(policy.show(1200))
    }
    @Test fun returnsToMusicFourSecondsAfterManeuverChanges() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn1", 90, 1000, 1000)
        policy.update("turn2", 900, 1500, 1500)
        policy.update("turn2", 850, 5000, 5000)
        assertTrue(policy.show(5499))
        assertFalse(policy.show(5500))
        policy.update("turn2", 100, 6000, 6000); assertTrue(policy.show(6000))
    }
    @Test fun consecutiveNearbyTurnsKeepMapVisible() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn1", 90, 1000, 1000)
        policy.update("turn2", 120, 1500, 1500)
        policy.update("turn2", 80, 5000, 5000)
        assertTrue(policy.show(5500))
    }
    @Test fun invalidStaleAndLostNavigationReturnToMusic() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn", 10, 1000, 1000)
        assertFalse(policy.show(6001))
        policy.update("turn", 10, 1000, 6001); assertFalse(policy.show(6001))
        policy.update("turn", 10, 7000, 7000)
        policy.update(null, 0, 7100, 7100); assertFalse(policy.show(7100))
        policy.update("turn", -1, 7200, 7200); assertFalse(policy.show(7200))
        policy.update("turn", 10, 7400, 7300); assertFalse(policy.show(7300))
    }
    @Test fun staleSessionCannotCarryItsLatchIntoTheNextSample() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn", 10, 1000, 1000)
        policy.update("turn", 900, 7000, 7000); assertFalse(policy.show(7000))
        policy.update("turn", 10, 7100, 7100)
        policy.reset(); assertFalse(policy.show(7100))
    }
}
