package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMapAutoPolicyTest {
    @Test fun speedThresholdUsesAdaptiveLeadTimeAtEveryBandBoundary() {
        for ((speed, seconds) in listOf(30 to 15, 40 to 15, 50 to 15,
            51 to 20, 70 to 20, 71 to 25, 90 to 25,
            91 to 30, 110 to 30, 111 to 45, 120 to 45)) {
            val meters = speed * seconds / 3.6
            assertEquals("speed=$speed", meters, ClusterMapAutoPolicy.triggerMeters(speed), 0.001)
        }
    }
    @Test fun thresholdRetainsSlowTrafficFloorAndUses1500MeterCeiling() {
        for (speed in listOf(0, 20, 24))
            assertEquals(100.0, ClusterMapAutoPolicy.triggerMeters(speed), 0.001)
        for (speed in listOf(120, 160, 300))
            assertEquals(1500.0, ClusterMapAutoPolicy.triggerMeters(speed), 0.001)
        for(speed in listOf(null,-1,301,Int.MAX_VALUE))
            assertEquals(200.0,ClusterMapAutoPolicy.triggerMeters(speed),0.001)
    }
    @Test fun highSpeedActivatesBeyondOldCeilingButNotBeyond1500Meters() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn", 1501, 1000, 1000, 160); assertFalse(policy.show(1000))
        policy.update("turn", 1500, 1100, 1100, 160); assertFalse(policy.show(1100))
        policy.update("turn", 1499, 1200, 1200, 160); assertTrue(policy.show(1200))
    }
    @Test fun accelerationCanTriggerButDecelerationCannotHideSameTurn() {
        val policy=ClusterMapAutoPolicy()
        policy.update("turn",240,1000,1000,30);assertFalse(policy.show(1000))
        policy.update("turn",240,1100,1100,60);assertTrue(policy.show(1100))
        policy.update("turn",230,1200,1200,0);assertTrue(policy.show(1200))
    }
    @Test fun nextManeuverUsesCurrentSpeedAfterFourSeconds() {
        val policy=ClusterMapAutoPolicy()
        policy.update("first",400,1000,1000,100)
        policy.update("next",300,1500,1500,100)
        policy.update("next",250,5000,5000,30)
        assertTrue(policy.show(5499));assertFalse(policy.show(5500))
    }
    @Test fun speedBasedBoundaryRemainsStrict() {
        val policy=ClusterMapAutoPolicy()
        policy.update("turn",125,1000,1000,30);assertFalse(policy.show(1000))
        policy.update("turn",124,1100,1100,30);assertTrue(policy.show(1100))
    }
    @Test fun cyclesDependOnNavigatorWithoutChangingSavedAuto() {
        assertEquals(ClusterMapMode.ON, ClusterMapMode.OFF.next(true))
        assertEquals(ClusterMapMode.AUTO, ClusterMapMode.ON.next(true))
        assertEquals(ClusterMapMode.OFF, ClusterMapMode.AUTO.next(true))
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
        policy.update(null, 0, 7100, 7100); assertTrue(policy.show(7100))
        policy.update("turn", -1, 7200, 7200); assertTrue(policy.show(7200))
        policy.update("turn", 10, 7400, 7300); assertTrue(policy.show(7300))
        assertFalse(policy.show(12001))
    }
    @Test fun transientReadFailureDoesNotHideOrExtendLease() {
        val messages = mutableListOf<String>()
        val policy = ClusterMapAutoPolicy { messages.add(it) }
        policy.update("turn", 90, 1000, 1000, 0)
        policy.unavailable(1500)
        assertTrue(policy.show(1500))
        policy.unavailable(5900)
        assertTrue(policy.show(6000))
        policy.unavailable(6001)
        assertFalse(policy.show(6001))
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("sample_expired"))
    }
    @Test fun recoveryDuringBrakingPreservesSameManeuverLatch() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn", 240, 1000, 1000, 60)
        policy.unavailable(1500)
        policy.update("turn", 230, 2000, 2000, 0)
        assertTrue(policy.show(2000))
    }
    @Test fun staleSessionCannotCarryItsLatchIntoTheNextSample() {
        val policy = ClusterMapAutoPolicy()
        policy.update("turn", 10, 1000, 1000)
        policy.update("turn", 900, 7000, 7000); assertFalse(policy.show(7000))
        policy.update("turn", 10, 7100, 7100)
        policy.reset(); assertFalse(policy.show(7100))
    }
}
