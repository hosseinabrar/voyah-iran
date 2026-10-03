package com.abrar.voicecommands

import com.abrar.raha.RahaWakeVolumeGate
import org.junit.Assert.*
import org.junit.Test

class RahaWakeVolumeGateTest {
    @Test fun disabledAllowsSilence() {
        assertTrue(RahaWakeVolumeGate().accept(0, 0, 32, 0))
    }
    @Test fun quietSpeechAndSingleSpikeAreRejected() {
        val gate=RahaWakeVolumeGate()
        for (t in 0L..1000L step 32) assertFalse(gate.accept(19,20,32,t))
        assertFalse(gate.accept(100,20,32,1024))
        assertFalse(gate.accept(0,20,32,1056))
    }
    @Test fun sustainedSpeechAllowsDecoderDelayButExpires() {
        val gate=RahaWakeVolumeGate()
        assertFalse(gate.accept(20,20,32,0))
        assertFalse(gate.accept(20,20,32,32))
        assertTrue(gate.accept(20,20,32,64))
        assertTrue(gate.accept(0,20,32,500))
        assertFalse(gate.accept(0,20,32,665))
    }
    @Test fun changingThresholdAndResetDiscardOldEnergy() {
        val gate=RahaWakeVolumeGate()
        for(t in 0L..64L step 32) gate.accept(40,20,32,t)
        assertFalse(gate.accept(40,50,32,96))
        for(t in 128L..192L step 32) gate.accept(60,50,32,t)
        gate.reset()
        assertFalse(gate.accept(0,50,32,224))
    }
}
