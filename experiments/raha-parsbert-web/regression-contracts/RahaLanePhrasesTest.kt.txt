package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Test
import org.junit.Assert.*

class RahaLanePhrasesTest {
    @Test fun laneControlsStaySeparate() {
        val cases=listOf(
            Triple("رادار بین خط رو فعال کن", "SET_LANE_SUPPORT_MODE", mapOf("enabled" to true)),
            Triple("رادار بین خطوط رو فعال کن", "SET_LANE_SUPPORT_MODE", mapOf("enabled" to true)),
            Triple("فعالسازی رانندگی بین خطوط", "SET_LANE_SUPPORT_MODE", mapOf("enabled" to true)),
            Triple("رادار بین خطوط رو غیر فعال کن", "SET_LANE_SUPPORT_MODE", mapOf("mode" to "off")),
            Triple("هشدار رانندگی بین خطوط رو غیر فعال کن", "SET_LANE_SUPPORT_MODE", mapOf("mode" to "off")),
            Triple("بین خطوط رانندگی کن", "SET_LANE_SUPPORT_MODE", mapOf("mode" to "keeping")),
            Triple("بین خطوط فقط هشدار بده", "SET_LANE_SUPPORT_MODE", mapOf("mode" to "warning")),
            Triple("بین خطوط با دخالت فرمان", "SET_LANE_SUPPORT_MODE", mapOf("mode" to "keeping")),
            Triple("هشدار رانندگی بین خطوط با ویبره", "SET_LANE_DEPARTURE_ALERT_TYPE", mapOf("type" to "vibration")),
            Triple("هشدار رانندگی بین خطوط با صدا", "SET_LANE_DEPARTURE_ALERT_TYPE", mapOf("type" to "voice")),
            Triple("هشدار رانندگی بین خطوط رو بذار رو حالت ویبره", "SET_LANE_DEPARTURE_ALERT_TYPE", mapOf("type" to "vibration")),
            Triple("هشدار رانندگی بین خطوط رو روی ویبره تنظیم کن", "SET_LANE_DEPARTURE_ALERT_TYPE", mapOf("type" to "vibration")),
            Triple("نوع هشدار بین خطوط رو بذار روی صدا", "SET_LANE_DEPARTURE_ALERT_TYPE", mapOf("type" to "voice")),
            Triple("حساسیت رادار بین خطوط رو زیاد کن", "SET_LANE_SUPPORT_MODE", mapOf("sensitivity" to "max")),
            Triple("رانندگی بین خطوط رو کم کن", "SET_LANE_SUPPORT_MODE", mapOf("sensitivity" to "min"))
        )
        val errors=mutableListOf<String>()
        for((i,c) in cases.withIndex()) {
            val result=RahaCommandRecognizer().recognize(c.first)
            val command=(result as? Recognition.Matched)?.command
            if(command?.action!=c.second || command.parameters!=c.third) errors+="case $i: ${c.first} -> $result expected=${c.third}"
        }
        assertTrue(errors.joinToString("\n"),errors.isEmpty())
    }
}
