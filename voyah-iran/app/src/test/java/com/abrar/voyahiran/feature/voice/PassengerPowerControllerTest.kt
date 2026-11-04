package com.abrar.voyahiran.feature.voice
import org.junit.Assert.*
import org.junit.Test
class PassengerPowerControllerTest {
    private class Port(var status: Int, val responds: Boolean = true): PassengerPowerPort {
        val writes=mutableListOf<Pair<Int,Boolean>>()
        override fun state(screen:Int):Int { assertEquals(2,screen);return status }
        override fun setOff(screen:Int,off:Boolean) { writes+=screen to off;if(responds)status=if(off)5 else 2 }
        override fun pause(milliseconds:Long) {}
    }
    @Test fun offOnAndRepeatNeverToggleOrTouchCentralDisplay() {
        val p=Port(2);val c=PassengerPowerController(p)
        c.execute(false);c.execute(false);c.execute(true);c.execute(true)
        assertEquals(listOf(2 to true,2 to false),p.writes)
    }
    @Test fun unknownOrBootStateDoesNotIssuePowerRequest() {
        for(state in listOf(-1,0,1,3,4,6,7,8)) {
            val p=Port(state)
            assertThrows(IllegalStateException::class.java){PassengerPowerController(p).execute(false)}
            assertTrue(p.writes.isEmpty())
        }
    }
    @Test fun ignoredOemRequestIsNotReportedAsSuccess() {
        val p=Port(2,false)
        assertThrows(IllegalStateException::class.java){PassengerPowerController(p).execute(false)}
        assertEquals(listOf(2 to true),p.writes)
    }
}
