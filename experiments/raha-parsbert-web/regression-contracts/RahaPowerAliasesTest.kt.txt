package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaPowerAliasesTest {
    private val parser=RahaCommandRecognizer()
    @Test fun reportedTargetsSharePowerSemantics() {
        for((target,action) in listOf("سردکن صندلی" to "SET_SEAT_VENTILATION",
            "هشدار عابر پیاده" to "SET_PEDESTRIAN_WARNING_SOUND",
            "رادار نقطه کور" to "SET_BLIND_SPOT_MONITORING",
            "نقطه کور" to "SET_BLIND_SPOT_MONITORING")) {
            for((enabled,verbs) in listOf(true to listOf("روشن کن","فعال کن"),false to listOf("خاموش کن","غیر فعال کن","غیرفعال کن","غیر\u200cفعال کن"))) {
                var reference:VehicleCommand?=null
                for(verb in verbs) {
                    val text="رها $target رو $verb"
                    val result=parser.recognize(text)
                    assertTrue("$text: $result",result is Recognition.Matched)
                    val command=(result as Recognition.Matched).command
                    assertEquals(action,command.action)
                    assertEquals(enabled,command.parameters["enabled"])
                    if(reference!=null)assertEquals(reference,command)
                    reference=command
                }
            }
        }
    }
}
