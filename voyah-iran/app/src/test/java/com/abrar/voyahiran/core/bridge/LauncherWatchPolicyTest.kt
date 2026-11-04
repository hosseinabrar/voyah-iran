package com.abrar.voyahiran.core.bridge

import org.junit.Test
import org.junit.Assert.*

class LauncherWatchPolicyTest {
    @Test fun irrelevantEventsDoNotWakePassengerButRelevantOnesDo(){
        assertTrue(LauncherWatchPolicy.unchanged(10,20,8))
        assertFalse(LauncherWatchPolicy.unchanged(10,20,15))
    }
    @Test fun freshClientAndRestartAlwaysReceiveFullSnapshot(){
        assertFalse(LauncherWatchPolicy.unchanged(-1,0,0))
        assertFalse(LauncherWatchPolicy.unchanged(100,3,2))
        assertFalse(LauncherWatchPolicy.unchanged(null,3,2))
    }
    @Test fun comfortAndDrivingAreRoutedSeparately(){
        for(key in listOf("FRONT_SEAT_HEATING_COMMAND_LEFT","FCM_SW_REQ_FB","FCM_DURATION_CONTROL"))
            assertEquals("passenger",LauncherWatchPolicy.scopeOf(key))
        for(key in listOf("speed","DRIVING_MODE_SET","AMBIENT_AIR_PRESSURE","IVI_SOC_MODESET"))
            assertEquals("central",LauncherWatchPolicy.scopeOf(key))
    }
    @Test fun scopedCommandsAreStrictlyAllowlisted(){
        for(scope in listOf("central","passenger")){
            val args=listOf("launcher-vehicle","watch-$scope","-1")
            assertEquals(args,BridgePolicy.resolve(args))
            assertNull(BridgePolicy.resolve(args+"extra"))
            assertNull(BridgePolicy.resolve(args.take(2)+"-2"))
        }
        assertNull(BridgePolicy.resolve(listOf("launcher-vehicle","watch-arbitrary","1")))
    }
}
