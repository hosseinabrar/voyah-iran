package com.abrar.voicecommands

import com.abrar.raha.*
import org.junit.Assert.*
import org.junit.Test

class RahaInputTextTest {
    @Test fun spacingIsCanonicalAndIdempotent() {
        val text=RahaInputText.spaces("  رها\u200c گرم\u200cکن  صندلی\tرو خاموش کن ")
        assertEquals("رها گرم کن صندلی رو خاموش کن",text)
        assertEquals(text,RahaInputText.spaces(text))
    }
    @Test fun sameCommandForSpaceAndHalfSpace() {
        val parser=RahaCommandRecognizer()
        for(text in listOf("رها گرم کن صندلی رو خاموش کن","رها سرد کن صندلی شاگرد رو روشن کن","رها شیشه رو تا نصفه بده پایین")) {
            val expected=parser.recognize(text)
            assertTrue("$text: $expected",expected is Recognition.Matched)
            assertEquals(expected,parser.recognize(text.replace(' ','\u200c')))
            assertEquals(expected,parser.recognize(text.replace(" "," \u200c  ")))
        }
        assertEquals(parser.recognize("گرم کن صندلی رو خاموش کن"),parser.recognize("گرمکن صندلی رو خاموش کن"))
    }
    @Test fun speechVocabularyHasNoHalfSpaceVariants() {
        for(phrases in listOf(RahaSpeechVocabulary.phrases(),RahaSpeechVocabulary.wakePhrases())) {
            assertFalse(phrases.any { '\u200c' in it })
            assertEquals(phrases.size,phrases.distinct().size)
        }
    }
}
