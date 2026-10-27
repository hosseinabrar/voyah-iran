package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaAppLaunchTest {
    private val recognizer = RahaCommandRecognizer()
    @Test fun mapVariants() {
        for (text in listOf("نقشه رو نشون بده", "نقشه رو نشان بده", "نقشه رو نمایش بده", "نقشه رو باز کن", "برنامه نقشه رو اجرا کن", "نقشه را باز کنید", "مسیریاب رو بیار بالا", "نشان رو باز کن", "برو رو نقشه", "برو روی برنامه نقشه", "برو روی برنامه نشان", "برو به نقشه", "لطفا بروید روی نقشه")) {
            val result = recognizer.recognize(text)
            assertTrue("$text: $result", result is Recognition.Matched)
            assertEquals(text, "OPEN_DEFAULT_MAP", (result as Recognition.Matched).command.action)
            if (text.contains("نشان") && !text.contains("نقشه")) assertEquals("neshan", result.command.parameters["requiredApp"])
        }
    }
    @Test fun requestedWindowPhrases() {
        for ((phrase, action) in listOf(
            "شیشه رو بکش پایین" to "OPEN_WINDOW",
            "شیشه رو بکش بالا" to "CLOSE_WINDOW",
            "شیشه رو بده پایین" to "OPEN_WINDOW",
            "شیشه رو بده بالا" to "CLOSE_WINDOW"
        )) {
            val result = recognizer.recognize(phrase)
            assertTrue("$phrase: $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(phrase, action, command.action)
            assertEquals(phrase, "driver", command.parameters["window"])
        }
    }
    @Test fun musicOpeningIsSeparateFromPlayback() {
        for (text in listOf("موزیک پلیر رو باز کن", "برنامه موزیک رو اجرا کن", "برنامه آهنگ رو اجرا کن", "برنامه موسیقی را اجرا کنید", "موزیک رو باز کن", "برو روی پخش", "پخش رو باز کن", "برنامه موزیک رو باز کن", "برو رو موزیک پلیر", "برو روی برنامه موزیک")) {
            val result = recognizer.recognize(text)
            assertTrue("$text: $result", result is Recognition.Matched)
            assertEquals(text, "OPEN_DEFAULT_MUSIC", (result as Recognition.Matched).command.action)
        }
        for (text in listOf("موزیک رو پلی کن", "آهنگ پخش کن", "موسیقی رو اجرا کن")) {
            assertEquals(text, "PLAY_MEDIA", (recognizer.recognize(text) as Recognition.Matched).command.action)
        }
    }
    @Test fun radioAlwaysOpensOemApp() {
        for(text in listOf("برو رو رادیو", "رادیو رو باز کن", "بازکردن رادیو", "باز کردن رادیو", "برنامه رادیو را اجرا کنید", "برو روی رادیو")) {
            val result=recognizer.recognize(text)
            assertTrue("$text => $result",result is Recognition.Matched)
            assertEquals("OPEN_OEM_RADIO",(result as Recognition.Matched).command.action)
            assertTrue(result.command.parameters.isEmpty())
        }
    }
}
