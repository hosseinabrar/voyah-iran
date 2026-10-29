package com.abrar.raha.intent

import org.junit.Assert.*
import org.junit.Test

class RahaMidpointsTest {
    @Test fun absoluteMidpoints() {
        for ((action,key,value) in listOf(
            Triple("SET_AUDIO_VOLUME","level",15),Triple("SET_CABIN_TEMPERATURE","degreesC",24),
            Triple("SET_WINDOW_POSITION","percent",50),Triple("SET_SUNSHADE_POSITION","position",50),
            Triple("SET_SEAT_HEATING","level",2),Triple("SET_FAN_SPEED","level",4))) {
            for(word in listOf("نصف","نصفه","وسط","متوسط")) {
                val r=RahaIntentGrounding.apply("مقدار رو تا $word زیاد کن",action,mapOf(key to value),mapOf(key to listOf(value)))
                assertNull(r.error);assertEquals(value,r.parameters[key]);assertTrue(key in r.grounded)
            }
        }
    }
    @Test fun halfDegreeIsStillFractional() {
        val r=RahaIntentGrounding.apply("دما رو نصف درجه زیاد کن","ADJUST_CABIN_TEMPERATURE",mapOf("delta" to 1),mapOf("delta" to listOf(.5,1)))
        assertNull(r.error);assertEquals(.5,r.parameters["delta"])
    }
    @Test fun halfClosingRemainsHalfOpen() {
        val r=RahaIntentGrounding.apply("شیشه رو تا نصفه ببند","SET_WINDOW_POSITION",mapOf("percent" to 0),mapOf("percent" to listOf(0,50,100)))
        assertNull(r.error);assertEquals(50,r.parameters["percent"])
    }
}
