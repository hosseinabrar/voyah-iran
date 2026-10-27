package com.abrar.voyahiran.feature.voice
import org.junit.Assert.*
import org.junit.Test
class ClimateStateControllerTest {
    @Test fun eachBinaryKeyChangesOnceAndRepeatedRequestIsNoOp() {
        for (field in ClimateStateController.feedback.keys.filter { it != "AC_RECIRC_AIR" && it != "AC_AUTO" }) {
            for (initial in 0..1) for (target in 0..1) {
                var state = initial; var writes = 0; var clock = 0L
                val controller = ClimateStateController({ state }, { assertEquals(field, it); writes++; state=1-state }, {}, { clock }, { clock+=it })
                controller.set(field,target); controller.set(field,target)
                assertEquals(target,state); assertEquals(if(initial==target)0 else 1,writes)
            }
        }
    }
    @Test fun autoOffPreservesFanAndNeverPulsesAuto() {
        for(speed in 0..7) {
            var mode=1;var clock=0L;val writes=mutableListOf<Int>()
            val c=ClimateStateController({if(it=="getAirMode")mode else speed},
                {fail("AUTO off must not send toggle")},{},{clock},{clock+=it},
                {writes+=it;mode=0})
            c.set("AC_AUTO",0);c.set("AC_AUTO",0)
            assertEquals(listOf(speed+1),writes)
        }
    }
    @Test fun autoOnStillUsesOnePulse() {
        var mode=0;var clock=0L;var pulses=0
        val c=ClimateStateController({mode},{assertEquals("AC_AUTO",it);pulses++;mode=1},{},{clock},{clock+=it})
        c.set("AC_AUTO",1);c.set("AC_AUTO",1)
        assertEquals(1,pulses)
    }
    @Test fun autoOffRequiresConfirmationAndDoesNotGuessInvalidSpeed() {
        for(speed in listOf(-1,8,3)) {
            var clock=0L;var writes=0
            val c=ClimateStateController({if(it=="getAirMode")1 else speed},
                {fail("Unexpected pulse")},{},{clock},{clock+=it},{writes++})
            try {c.set("AC_AUTO",0);fail("Must reject invalid or unchanged feedback")}catch(_:IllegalStateException){}
            assertEquals(if(speed==3)1 else 0,writes)
        }
    }
    @Test fun circulationUsesOnlyConfirmedSteps() {
        for(initial in 0..2) for(target in 0..2) {
            var state=initial; var writes=0; var clock=0L
            val c=ClimateStateController({state},{writes++;state=(state+1)%3},{},{clock},{clock+=it})
            c.set("AC_RECIRC_AIR",target);c.set("AC_RECIRC_AIR",target)
            assertEquals(target,state);assertEquals((target-initial+3)%3,writes)
        }
    }
    @Test fun staleFeedbackNeverCausesSecondToggle() {
        var writes=0;var clock=0L
        val c=ClimateStateController({0},{writes++},{},{clock},{clock+=it})
        try { c.set("AC_RECIRC_AIR",2);fail("unchanged feedback must fail") } catch(_:IllegalStateException){}
        assertEquals(1,writes)
    }
    @Test fun delayedFeedbackIsAwaitedBeforeNextCycle() {
        var clock=0L;var state=0;var pending=-1;var sentAt=0L;var writes=0
        val c=ClimateStateController({if(pending>=0 && clock-sentAt>=500){state=pending;pending=-1};state},
            {assertEquals(-1,pending);writes++;pending=(state+1)%3;sentAt=clock},{},{clock},{clock+=it})
        c.set("AC_RECIRC_AIR",2);assertEquals(2,writes);assertEquals(2,state)
    }
}
