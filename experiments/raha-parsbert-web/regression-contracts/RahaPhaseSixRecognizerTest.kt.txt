package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import com.abrar.raha.RahaPhaseSixRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaPhaseSixRecognizerTest {
    private val parser = RahaCommandRecognizer()
    private fun check(text: String, action: String, params: Map<String, Any>) {
        val result = parser.recognize(text)
        assertTrue("$text -> $result", result is Recognition.Matched)
        val command = (result as Recognition.Matched).command
        assertEquals(text, action, command.action)
        assertEquals(text, params, command.parameters)
    }
    @Test fun chargingStopAliases() {
        listOf("شارژ رو استوپ کن", "شارژ رو قطع کن", "شارژر رو قطع کن", "شارژر رو آزاد کن",
            "شارژ را استاپ کنید", "استوپ کردن شارژ", "شارژ رو متوقف کن")
            .forEach { check(it, "STOP_CHARGING", emptyMap()) }
        check("موزیک رو استوپ کن", "PAUSE_MEDIA", emptyMap())
        check("موزیک رو استاپ کن", "PAUSE_MEDIA", emptyMap())
    }
    @Test fun energyAliasesAndWordOrder() {
        for ((mode, names) in RahaPhaseSixRecognizer.strategies) for (name in names.split('|')) {
            for (sentence in listOf("حالت $name", "ماشین رو بذار تو مد $name", "$name رو فعال کن", "لطفا حالت مصرف انرژی را روی $name تنظیم کنید"))
                check(sentence, "SET_ENERGY_STRATEGY", mapOf("strategy" to mode))
        }
    }
    @Test fun sceneAliasesAndWordOrder() {
        for ((scene, names) in RahaPhaseSixRecognizer.scenes) for (name in names.split('|')) {
            for (sentence in listOf("حالت $name", "$name رو فعال کن", "لطفا سناریوی $name را روشن کنید"))
                check(sentence, "ACTIVATE_SCENE", mapOf("scene" to scene))
            for (sentence in listOf("$name رو خاموش کن", "از حالت $name خارج شو"))
                check(sentence, "DEACTIVATE_SCENE", mapOf("scene" to "current"))
        }
        check("از سناریوی فعلی خارج شو", "DEACTIVATE_SCENE", mapOf("scene" to "current"))
    }
    @Test fun chargeAndReserve() {
        for ((word, n) in mapOf("بیست و پنج" to 25, "سی" to 30, "چهل و پنج" to 45, "پنجاه" to 50, "هشتاد" to 80)) {
            check("حد حفظ شارژ باتری رو روی $word درصد بذار", "SET_BATTERY_RESERVE_TARGET", mapOf("percent" to n))
            check("حفظ شارژ $word درصد", "SET_BATTERY_RESERVE_TARGET", mapOf("percent" to n))
        }
        for (s in listOf("شارژ رو قطع کن", "شارژ کردن رو متوقف کن", "توقف شارژ", "شارژ باتری را متوقف کنید"))
            check(s, "STOP_CHARGING", emptyMap())
        check("کنترل دمای باتری رو روشن کن", "SET_BATTERY_PRECONDITIONING", mapOf("enabled" to true))
        check("پیش گرمایش باتری رو خاموش کن", "SET_BATTERY_PRECONDITIONING", mapOf("enabled" to false))
    }
    @Test fun powerHoldConfiguration() {
        for ((word, n) in mapOf("نیم ساعت" to 30, "یک ساعت" to 60, "یک و نیم ساعت" to 90, "دو ساعت" to 120, "سه ساعت" to 180, "نامحدود" to -1, "صد و پنجاه دقیقه" to 150))
            check("زمان حفظ برق رو روی $word تنظیم کن", "SET_POWER_KEEP_DURATION", mapOf("minutes" to n))
        check("شارژ با موتور در حالت حفظ برق رو فعال کن", "SET_POWER_KEEP_ENGINE_CHARGING", mapOf("enabled" to true))
        check("در حالت حفظ برق شارژ با بنزین رو خاموش کن", "SET_POWER_KEEP_ENGINE_CHARGING", mapOf("enabled" to false))
    }
    @Test fun mirrorRecognitionDoesNotImplyHardwareSupport() {
        check("آینه ها رو جمع کن", "SET_MIRROR_FOLD", mapOf("side" to "all", "folded" to true))
        check("آینه راننده رو باز کن", "SET_MIRROR_FOLD", mapOf("side" to "driver", "folded" to false))
    }
    @Test fun invalidTargetsDoNotBecomeActions() {
        for (s in listOf("حفظ شارژ ۸۳ درصد", "زمان حفظ برق ۵ ساعت", "شارژ باتری", "آینه ها"))
            assertTrue("$s", parser.recognize(s) is Recognition.Unknown)
    }
}
