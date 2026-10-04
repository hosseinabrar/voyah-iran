package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherVehicleCacheTest {
    private val initial=LauncherVehicleState(energy=3,recovery=3,height=5,fresh=true,modes=mapOf("electric" to 2,"fuel" to 3))
    @Test fun oldFeedbackDoesNotUndoImmediateSelection() {
        val c=LauncherVehicleCache();c.accept(initial)
        val r=c.request("energy","electric")
        c.accept(initial.copy(pressure=890))
        assertEquals(2,c.display.energy);assertEquals(890,c.display.pressure)
        assertTrue(c.pending(r));assertTrue(c.display.fresh)
        c.accept(initial.copy(energy=2));assertFalse(c.pending(r))
        c.finish(r);assertEquals(2,c.display.energy)
    }
    @Test fun rejectionRollsBackOnlyAffectedControlToLatestFeedback() {
        val c=LauncherVehicleCache();c.accept(initial)
        val energy=c.request("energy","electric");c.request("recovery","4")
        c.accept(initial.copy(energy=1));c.finish(energy)
        assertEquals(1,c.display.energy);assertEquals(4,c.display.recovery)
    }
    @Test fun oldRequestCannotClearNewSelectionAndHeightAnimatesImmediately() {
        val c=LauncherVehicleCache();c.accept(initial)
        val old=c.request("height","1");val next=c.request("height","0")
        c.finish(old);assertTrue(c.pending(next));assertEquals(20,c.display.bodyFrame)
        c.accept(initial.copy(height=8,adjusting=2))
        assertEquals(9,c.display.height)
        c.finish(next);assertEquals(8,c.display.height)
    }
}
