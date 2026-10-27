package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaColloquialRegressionTest {
    private val recognizer = RahaCommandRecognizer()
    private fun same(reference: String, actual: String) {
        val expected = recognizer.recognize(reference) as Recognition.Matched
        val result = recognizer.recognize(actual)
        assertTrue("$actual => $result", result is Recognition.Matched)
        result as Recognition.Matched
        assertEquals(actual, expected.command.action, result.command.action)
        assertEquals(actual, expected.command.parameters, result.command.parameters)
    }
    @Test fun reportedWindowPhrases() {
        for (amount in listOf("یکم", "یه مقدار", "مقداری", "کم"))
            same("شیشه راننده رو کمی باز کن", "شیشه راننده رو $amount بکش پایین")
    }
    @Test fun windowAndRoofVariations() {
        for (target in listOf("شیشه راننده", "شیشه شاگرد", "پنجره عقب چپ", "پنجره ها", "سانروف", "پرده"))
            for (amount in listOf("یکم", "یه مقدار", "مقداری", "کم", "یه ذره"))
                for ((direction, verb) in listOf("باز" to "باز کنید", "ببند" to "ببند"))
                    same("$target رو کمی $direction کن", "لطفا $target رو $amount $verb")
        for (verb in listOf("بکش پایین", "بکش به پایین", "بده پایین", "بیار پایین", "پایین بکش"))
            same("شیشه رو کمی باز کن", "شیشه رو یه مقدار $verb")
    }
    @Test fun softAmountsAcrossLevelCommands() {
        for (target in listOf("فن", "کولر", "بخاری", "دما", "گرمکن صندلی", "سردکن صندلی", "ماساژ", "رایحه", "نورپردازی داخلی", "حساسیت برف پاک کن"))
            for (direction in listOf("زیاد", "کم"))
                for (amount in listOf("یکم", "یه کم", "یه مقدار", "مقداری", "اندکی", "یک ذره"))
                    same("$target رو $direction کن", "$target رو $amount $direction کن")
        for (target in listOf("فن", "سردکن صندلی", "ماساژ", "رایحه", "حساسیت برف پاک کن"))
            same("$target رو زیاد کن", "$target رو کم زیاد کن")
    }
    @Test fun incompleteWeatherAdjectives() {
        for ((adjective, spoken) in listOf("گرم" to "گرمه", "سرد" to "سرده"))
            for (prefix in listOf("هوا", "هوای اتاق", "داخل ماشین"))
                for (degree in listOf("", "خیلی"))
                    same("هوا $degree $spoken", "$prefix $degree $adjective")
    }
}
