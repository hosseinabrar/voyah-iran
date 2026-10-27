package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import com.abrar.raha.RahaSpeechVocabulary
import org.junit.Assert.*
import org.junit.Test

class RahaChargeVentHillAliasesTest {
    private val parser = RahaCommandRecognizer()
    private fun expect(text: String, action: String, parameters: Map<String, Any>) {
        val result = parser.recognize(text)
        assertTrue("$text -> $result", result is Recognition.Matched)
        val command = (result as Recognition.Matched).command
        assertEquals(text, action, command.action)
        assertEquals(text, parameters, command.parameters)
    }

    @Test fun chargingPortAliasesAndOrders() {
        for (noun in listOf("شارژر", "پورت شارژ", "درگاه شارژ", "سوکت شارژ", "درپوش شارژر"))
            for (sentence in listOf("$noun رو باز کن", "$noun را باز کنید", "باز کردن $noun", "باز کن $noun"))
                expect(sentence, "OPEN_CHARGE_PORT", emptyMap())
    }

    @Test fun ventPresetWithAndWithoutWindowNoun() {
        for (text in listOf("حالت ونت رو فعال کن", "ونت", "ونت رو روشن کن",
            "شیشه ها رو ببر تو حالت ونت", "همه شیشه ها رو بذار روی ونت",
            "پنجره ها را در حالت ونت قرار دهید", "ونت شیشه ها رو فعال کن"))
            expect(text, "VENT_WINDOW", mapOf("window" to "all"))
        expect("شیشه راننده رو ونت کن", "VENT_WINDOW", mapOf("window" to "driver"))
        expect("تهویه رو روشن کن", "SET_CLIMATE_POWER", mapOf("enabled" to true, "zone" to "front", "acEnabled" to false))
    }

    @Test fun hillDescentWithSystemAndImplicitEnable() {
        for (noun in listOf("کنترل سرپایینی", "کنترل سر پایینی", "کنترل سرازیری"))
            for (prefix in listOf("", "سیستم ", "سامانه ")) {
                expect("$prefix$noun", "SET_HILL_DESCENT_CONTROL", mapOf("enabled" to true))
                expect("$prefix$noun رو فعال کن", "SET_HILL_DESCENT_CONTROL", mapOf("enabled" to true))
                expect("$prefix$noun را خاموش کنید", "SET_HILL_DESCENT_CONTROL", mapOf("enabled" to false))
            }
    }

    @Test fun speechVocabularyIncludesReportedPhrases() {
        val vocabulary = RahaSpeechVocabulary.phrases()
        for (text in listOf("شارژر رو باز کن", "پورت شارژ رو باز کن", "حالت ونت رو فعال کن",
            "شیشه ها رو ببر تو حالت ونت", "سیستم کنترل سرپایینی رو فعال کن", "سیستم کنترل سرپایینی"))
            assertTrue(text, text in vocabulary)
    }
}
