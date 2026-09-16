package com.abrar.voicecommands

import com.abrar.raha.RahaWakeGate
import org.junit.Assert.*
import org.junit.Test

class RahaWakeGateTest {
    @Test fun timeoutIsEnforcedWithoutExternalPolling() {
        val gate=RahaWakeGate(setOf("رها"),100)
        gate.final("رها",0)
        assertNull(gate.final("اکو",100))
        assertFalse(gate.active)
        assertEquals("اسپورت",gate.final("رها اسپورت",101))
    }
    @Test fun expiredPartialDoesNotAdmitSpeech() {
        val gate=RahaWakeGate(setOf("رها"),100)
        gate.final("رها",0)
        assertNull(gate.partial("شیشه باز",101))
        assertFalse(gate.active)
    }
    @Test fun emptyOrUnselectedWakeCannotActivate() {
        val gate=RahaWakeGate(setOf("", " ", "  هی   رها "))
        assertNull(gate.final("رها اکو",0))
        assertNull(gate.final("اکو",1))
        assertEquals("اکو",gate.final("هی رها اکو",2))
    }
    @Test(expected=IllegalArgumentException::class)
    fun timeoutMustBePositive() { RahaWakeGate(setOf("رها"),0) }
}
