package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class VehicleSnapshotOrderTest {
    @Test fun olderWatchCannotUndoReadback(){
        val order=VehicleSnapshotOrder()
        assertTrue(order.accept(100,7));assertFalse(order.accept(100,6))
        assertTrue(order.accept(100,7));assertTrue(order.accept(100,8))
    }
    @Test fun newSessionMayRestartRevisionButOldSessionCannotReturn(){
        val order=VehicleSnapshotOrder()
        assertTrue(order.accept(100,70));assertTrue(order.accept(200,0))
        assertFalse(order.accept(100,100));assertTrue(order.accept(200,1))
    }
}
