package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaSportIdiomsTest {
    @Test fun colloquialSportRequests() {
        val phrases = listOf(
            "آدرنالین", "آدرنالینش رو ببر بالا", "یکم آدرنالین تزریق کن", "هیجان بده",
            "هیجان رو ببر بالا", "می خوام پرواز کنم", "بریم رو مود پرواز", "بریم بترکونیم",
            "ادرنالین رو زیاد کن", "کمی آدرنالین بده", "یه کم هیجان بده", "هیجان بیشتری بده",
            "آدرنالینش رو افزایش بده", "بریم پرواز کنیم", "میخوام پرواز کنم",
            "بزن بریم بترکونیم", "حالت پرواز رو فعال کن", "لطفا یکم هیجان تزریق کن"
        )
        val parser = RahaCommandRecognizer()
        for (phrase in phrases) {
            val result = parser.recognize(phrase)
            assertTrue("$phrase: $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(phrase, "SET_DRIVE_MODE", command.action)
            assertEquals(phrase, mapOf("mode" to "sport"), command.parameters)
        }
    }
}
