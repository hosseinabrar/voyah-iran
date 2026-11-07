package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaAdasSynonymsTest {
    @Test fun pedestrianAliasesRemainWarningSound() {
        for(target in listOf("هشدار عابر پیاده", "رادار عابر پیاده", "رادار عابر", "سنسور عابر", "رادار هشدار عابر پیاده", "صدای هشدار عابر")) {
            for(enabled in listOf(true,false)) {
                val phrase="$target رو ${if(enabled)"فعال" else "غیر فعال"} کن"
                val result=RahaCommandRecognizer().recognize(phrase)
                assertTrue("$phrase -> $result", result is Recognition.Matched)
                val command=(result as Recognition.Matched).command
                assertEquals(phrase,"SET_PEDESTRIAN_WARNING_SOUND",command.action)
                assertEquals(phrase,mapOf("enabled" to enabled),command.parameters)
            }
        }
    }
    @Test fun optionalSensorRadarWarningLabels() {
        val targets=mapOf(
            "باز کردن در" to "SET_DOOR_OPEN_WARNING",
            "بازکردن در" to "SET_DOOR_OPEN_WARNING",
            "تصادف جلو" to "SET_FORWARD_COLLISION_WARNING",
            "ترافیک عقب" to "SET_REAR_CROSS_TRAFFIC_WARNING",
            "تصادف از عقب" to "SET_REAR_COLLISION_WARNING",
            "ترمز خودکار" to "SET_AUTOMATIC_EMERGENCY_BRAKING",
            "ترمز اظطراری" to "SET_AUTOMATIC_EMERGENCY_BRAKING",
            "نقطه کور" to "SET_BLIND_SPOT_MONITORING",
            "حرکت خودروی جلو" to "SET_LEAD_VEHICLE_DEPARTURE_ALERT",
            "حرکت ترافیک" to "SET_LEAD_VEHICLE_DEPARTURE_ALERT",
            "ترافیک جلو" to "SET_FRONT_CROSS_TRAFFIC_WARNING"
        )
        val errors=mutableListOf<String>()
        for((target,action) in targets) for(label in listOf("رادار","سنسور","هشدار","رادار هشدار","سنسور هشدار")) for(enabled in listOf(true,false)) {
            val phrase="$label $target رو ${if(enabled)"فعال" else "غیر فعال"} کن"
            val command=(RahaCommandRecognizer().recognize(phrase) as? Recognition.Matched)?.command
            if(command?.action!=action || command.parameters!=mapOf("enabled" to enabled)) errors+="$phrase -> $command"
        }
        assertTrue(errors.joinToString("\n"),errors.isEmpty())
    }
}
