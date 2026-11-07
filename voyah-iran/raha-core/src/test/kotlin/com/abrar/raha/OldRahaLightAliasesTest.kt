package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class OldRahaLightAliasesTest {
    @Test fun spokenAndPluralFormsKeepTheSameActionAndParameters() {
        for(parser in listOf(OldRahaRecognizer(),OldRahaRecognizer { "".byteInputStream() })) {
            for(tail in listOf("رو روشن کن","رو خاموش کن","جلو رو روشن کن","رو روی اتو تنظیم کن","کوچک رو روشن کن","داخل رو روشن کن","داخل ماشین رو کم کن")) {
                val baseline=parser.recognize("چراغ $tail")
                assertTrue("چراغ $tail => $baseline",baseline is Recognition.Matched)
                for(form in listOf("چراغ ای","چراغها","چراغ های","چراغای","چراغ","چراغ‌ها","چراغ‌های")) {
                    val result=parser.recognize("$form $tail")
                    assertTrue("$form $tail => $result",result is Recognition.Matched)
                    assertEquals((baseline as Recognition.Matched).command.action,(result as Recognition.Matched).command.action)
                    assertEquals(baseline.command.parameters,result.command.parameters)
                }
            }
        }
    }
    @Test fun qualifiersAreNotDiscarded() {
        assertEquals("چراغ داخل ماشین",RahaLightWords.normalize("چراغای داخل ماشین"))
        assertEquals("چراغ کوچک",RahaLightWords.normalize("چراغ های کوچک"))
        assertEquals("چراغانی",RahaLightWords.normalize("چراغانی"))
    }
}
