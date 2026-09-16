package com.abrar.voicecommands
import com.abrar.raha.*
import org.junit.Assert.*
import org.junit.Test

class RahaWakeConfirmationTest {
    @Test fun rejectsTransientAndLowConfidenceName() {
        val filter=RahaWakeConfirmation(WakeSensitivity.BALANCED)
        assertFalse(filter.accept("رها",0.95,0,false))
        assertFalse(filter.accept("رها",0.95,100,false))
        assertTrue(filter.accept("آدم شاخ در میاره",null,200,false))
        assertFalse(filter.accept("رها",0.95,300,false))
        assertFalse(filter.accept("رها",0.4,900,true))
        assertFalse(filter.accept("رها",null,1000,true))
    }
    @Test fun confirmsStableNameWithoutLosingJoinedCommand() {
        val filter=RahaWakeConfirmation(WakeSensitivity.BALANCED)
        val gate=RahaWakeGate(setOf("رها"))
        assertFalse(filter.accept("رها",0.95,0,false))
        val joined="رها شیشه را باز کن"
        assertTrue(filter.accept(joined,0.95,300,false))
        assertEquals("شیشه را باز کن",gate.partial(joined,300))
        assertEquals("شیشه را باز کن",gate.final(joined,500))
    }
    @Test fun finalWakeAllowsFollowingSegment() {
        val filter=RahaWakeConfirmation(WakeSensitivity.STRICT)
        val gate=RahaWakeGate(setOf("رها"))
        assertTrue(filter.accept("رها",0.96,0,true))
        assertNull(gate.final("رها",0))
        assertEquals("کولر بگیر",gate.final("کولر بگیر",500))
    }
    @Test fun compoundWakesAndLegacyAreUnchanged() {
        for (mode in WakeSensitivity.values()) {
            val filter=RahaWakeConfirmation(mode)
            assertTrue(filter.accept("هی رها کولر بگیر",null,0,false))
            assertTrue(filter.accept("سلام رها",null,0,false))
        }
        assertTrue(RahaWakeConfirmation(WakeSensitivity.LEGACY).accept("رها",null,0,false))
    }
}
