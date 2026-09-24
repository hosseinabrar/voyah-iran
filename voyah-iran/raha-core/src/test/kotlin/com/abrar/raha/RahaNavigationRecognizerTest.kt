package com.abrar.raha

import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class RahaNavigationRecognizerTest {
    private val recognizer = RahaCommandRecognizer()
    @Test fun variedWordOrdersAndAliases() {
        for ((id, aliases) in RahaNavigationRecognizer.aliases) for (name in aliases) {
            for (sentence in listOf(name, "بریم $name", "$name رو مسیریابی کن", "مسیریابی به $name", "مسیریابی تا $name", "لطفا منو ببر $name", "مسیر یابی به $name", "$name را مسیریابی کنید", "برو سمت $name") + RahaNavigationRecognizer.requests.map { "$it $name" }) {
                val result = recognizer.recognize(sentence)
                assertTrue("$sentence -> $result", result is Recognition.Matched)
                val command = (result as Recognition.Matched).command
                assertEquals(sentence, "NAVIGATE_TO_SAVED_PLACE", command.action)
                assertEquals(sentence, id, command.parameters["destination"])
            }
        }
    }
    @Test fun halfSpaceAndFormalSpeech() {
        val result = recognizer.recognize("لطفاً تعمیرگاه شاه‌پری را مسیریابی کنید") as Recognition.Matched
        assertEquals("shahpary", result.command.parameters["destination"])
    }
    @Test fun unrelatedSpeechDoesNotSelectADealer() {
        for (text in listOf("نمایندگی مرکزی کجاست", "مرکزی", "سروش", "نمایندگی مرکزی زنگ بزن"))
            assertFalse(text, recognizer.recognize(text) is Recognition.Matched)
    }
    @Test fun twoDestinationsFollowExistingFirstCommandPolicy() {
        val result = recognizer.recognize("نمایندگی مرکزی و شاهپری") as Recognition.Matched
        assertEquals("soroush_central", result.command.parameters["destination"])
    }
    @Test fun vocabularyContainsDestinationPhrases() {
        val phrases = RahaSpeechVocabulary.phrases()
        assertTrue(phrases.contains("نمایندگی شاه پری"))
        assertTrue(phrases.contains("بریم نمایندگی مرکزی"))
    }
}
