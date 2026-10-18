package com.abrar.voyahiran.feature.launcher

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class PassengerCardStateTest {
    @Test fun unrelatedUpdatesDoNotChangeCardSnapshots(){
        val s=JSONObject().put("fresh",true).put("getAirSWStatus",1).put("FCM_SW_REQ_FB",2)
            .put("FRONT_SEAT_MASS_SWITCH_LEFT",2).put("revision",1).put("speed",0)
        for(card in listOf("climate","seats","fragrance")){
            val before=PassengerCardState.select(s,card).toString()
            s.put("revision",200).put("speed",80).put("audioRevision",15)
            assertEquals(before,PassengerCardState.select(s,card).toString())
            assertFalse(PassengerCardState.select(s,card).has("revision"))
        }
        assertTrue(PassengerCardState.select(s,"climate").has("getAirSWStatus"))
        assertFalse(PassengerCardState.select(s,"climate").has("FCM_SW_REQ_FB"))
        s.put("fresh",false)
        for(card in listOf("climate","seats","fragrance"))assertFalse(PassengerCardState.select(s,card).getBoolean("fresh"))
    }
}
