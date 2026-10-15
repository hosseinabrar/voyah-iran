package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class PassengerComfortPolicyTest {
    @Test fun onlyBoundedComfortCommandsAreAccepted() {
        for(side in listOf("driver","passenger")) {
            for(n in listOf(155,220,225,305))assertTrue(PassengerComfortBridge.valid("$side-temp","$n"))
            for(n in listOf("154","306","221","NaN","22.5"))assertFalse(PassengerComfortBridge.valid("$side-temp",n))
            for(feature in listOf("vent","heat","massage")) {
                for(n in 0..3)assertTrue(PassengerComfortBridge.valid("$side-$feature","$n"))
                assertFalse(PassengerComfortBridge.valid("$side-$feature","4"))
            }
            assertTrue(PassengerComfortBridge.valid("$side-mode","1"))
            assertTrue(PassengerComfortBridge.valid("$side-mode","2"))
            assertFalse(PassengerComfortBridge.valid("$side-mode","0"))
        }
        assertFalse(PassengerComfortBridge.valid("drive","3"))
        assertFalse(PassengerComfortBridge.valid("fan","8"))
        assertFalse(PassengerComfortBridge.valid("intensity","0"))
        assertFalse(PassengerComfortBridge.valid("power","true"))
        assertFalse(PassengerComfortBridge.valid("scent","1;reboot"))
        assertTrue(PassengerComfortBridge.valid("scent","0"))
    }
}
