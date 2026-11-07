package com.abrar.voicecommands
import com.abrar.raha.*
import org.junit.Assert.*
import org.junit.Test
class RahaIgnoredSpeechTest {
    @Test fun ignoredPhrasesCancelPendingWakeAndNeverMapCommands() {
        for (text in RahaIgnoredSpeech.phrases + listOf("رهایم کن رهایم کن", "رها آدما", "رها رهایم کن")) {
            val gate=RahaWakeGate(setOf("رها"))
            gate.partial("رها", 0)
            assertNull(text, gate.partial(text, 50))
            assertFalse(text, gate.active)
            gate.final("رها", 100)
            assertNull(text, gate.final(text, 200))
            assertFalse(text, gate.active)
            assertTrue(text, RahaCommandRecognizer().recognize(text) is Recognition.Unknown)
        }
    }
    @Test fun realHeatingAndTemperatureCommandsStillPassGate() {
        for (text in listOf("گرم کن صندلی رو فعال کن", "دما رو زیاد کن")) {
            val gate=RahaWakeGate(setOf("رها"))
            assertEquals(text, gate.final("رها $text", 0))
            assertTrue(RahaCommandRecognizer().recognize(text) is Recognition.Matched)
        }
    }
    @Test fun acousticAlternativesAreInBothGrammars() {
        assertTrue(RahaSpeechVocabulary.phrases().containsAll(RahaIgnoredSpeech.phrases))
        assertTrue(RahaSpeechVocabulary.wakePhrases().containsAll(RahaIgnoredSpeech.phrases))
    }
}
