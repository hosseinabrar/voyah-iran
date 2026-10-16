package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PassengerComfortCacheTest {
    private fun state(vararg fields:Pair<String,Int>)=JSONObject().put("fresh",true).apply{fields.forEach{put(it.first,it.second)}}
    @Test fun oldFeedbackCannotUndoFanAndFailureRestoresLatestActual(){
        val c=PassengerComfortCache();c.accept(state("getAirWindSpeed" to 2))
        val r=c.request("fan","5")
        assertEquals(5,c.display.getInt("getAirWindSpeed"))
        c.accept(state("getAirWindSpeed" to 3,"getAirLeftTemperature" to 230))
        assertEquals(5,c.display.getInt("getAirWindSpeed"))
        assertEquals(230,c.display.getInt("getAirLeftTemperature"))
        c.finish(r);assertEquals(3,c.display.getInt("getAirWindSpeed"))
    }
    @Test fun seatSwitchAndLevelMustBothConfirm(){
        val c=PassengerComfortCache()
        c.accept(state("FRONT_SEAT_MASS_SWITCH_RIGHT" to 1,"FRONT_SEAT_MASS_INTEN_RIGHT" to 3))
        val r=c.request("passenger-massage","2")
        assertEquals(2,PassengerSeatFeedback.level(c.display,1,"massage"))
        c.accept(state("FRONT_SEAT_MASS_SWITCH_RIGHT" to 1,"FRONT_SEAT_MASS_INTEN_RIGHT" to 2))
        assertTrue(c.pending(r))
        c.accept(state("FRONT_SEAT_MASS_SWITCH_RIGHT" to 2,"FRONT_SEAT_MASS_INTEN_RIGHT" to 2))
        assertFalse(c.pending(r))
    }
    @Test fun offDoesNotRequireRememberedSeatLevelToBecomeZero(){
        val c=PassengerComfortCache();val r=c.request("driver-vent","0")
        c.accept(state("FRONT_SEAT_VENTILATION_SWITCH_LEFT" to 1,"FRONT_SEAT_VENTILATION_COMMAND_LEFT" to 3))
        assertFalse(c.pending(r));assertEquals(0,PassengerSeatFeedback.level(c.display,0,"vent"))
    }
    @Test fun independentControlsAndSupersededTimeoutsStayIndependent(){
        val c=PassengerComfortCache();c.accept(state("getAirWindSpeed" to 1,"getAirACStatus" to 0))
        val old=c.request("fan","2");val latest=c.request("fan","3");c.request("ac","1")
        c.finish(old);assertTrue(c.pending(latest));assertEquals(3,c.display.getInt("getAirWindSpeed"))
        c.finish(latest);assertEquals(1,c.display.getInt("getAirWindSpeed"));assertEquals(1,c.display.getInt("getAirACStatus"))
    }
    @Test fun staleFeedbackDoesNotConfirmOrReenableControls(){
        val c=PassengerComfortCache();c.accept(state("getAirWindSpeed" to 1))
        val r=c.request("fan","2");c.stale()
        assertFalse(c.display.getBoolean("fresh"));assertTrue(c.pending(r))
        c.accept(state("getAirWindSpeed" to 2).put("fresh",false));assertTrue(c.pending(r))
    }
    @Test fun fragranceAndSyncUseFeedbackEncoding(){
        assertEquals(mapOf("FCM_SW_REQ_FB" to 2,"IVI_FRAG_TASTE_FB" to 1),PassengerComfortCache.feedback("scent","1"))
        assertEquals(mapOf("FCM_SW_REQ_FB" to 1),PassengerComfortCache.feedback("scent","0"))
        assertEquals(mapOf("getAirDUALStatus" to 0),PassengerComfortCache.feedback("sync","1"))
        assertEquals(mapOf("getAirSupplyStatus" to 4),PassengerComfortCache.feedback("airflow","windshield"))
        assertEquals(mapOf("getAirWindSpeed" to 7),PassengerComfortCache.feedback("fan","7"))
    }
    @Test fun queuedSelectionCannotBeConfirmedBeforeDispatch(){
        val c=PassengerComfortCache();c.accept(state("getAirWindSpeed" to 1))
        val r=c.request("fan","1");r.dispatched=false
        c.accept(state("getAirWindSpeed" to 1));assertTrue(c.pending(r))
        r.dispatched=true;c.accept(state("getAirWindSpeed" to 1));assertFalse(c.pending(r))
    }
}
