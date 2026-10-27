package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaReleasePhrasesTest {
    @Test fun reportedPhrasesPreserveSemanticSlots() {
        val pairs = listOf(
            "تهویه اتاق رو روشن کن" to "تهویه رو روشن کن",
            "ای سی کولر رو روشن کن" to "ای سی رو روشن کن",
            "ای سی ماشین رو روشن کن" to "ای سی رو روشن کن",
            "کولر رو تا انتها زیاد کن" to "حداکثر سرمایش",
            "مد اسپورت" to "مود اسپورت",
            "فعال سازی تهویه خودکار" to "تهویه خودکار",
            "تهویه رو بصورت خودکار در بیار" to "تهویه خودکار",
            "تهویه به شکل خودکار انجام بشه" to "تهویه خودکار",
            "دمای اتاق زیاد شود" to "دمای اتاق رو زیاد کن",
            "باد به سمت پایین بوزه" to "باد به سمت پا",
            "باد رو بده پایین" to "باد به سمت پا",
            "رایحه بامبو رو برای مدت سی دقیقه با حداکثر شدت پخش کن" to "رایحه بامبو سی دقیقه با حداکثر شدت روشن کن",
            "ماساژ غلتکی رو فعال کن" to "ماساژ رولینگ",
            "ماساژ چرخشی رو فعال کن" to "ماساژ رولینگ",
            "ماساژ ویوینگ" to "ماساژ موجی",
            "وضعیت صندلی رو ذخیره کن" to "موقعیت صندلی رو ذخیره کن",
            "شیشه ها رو تو حالت تهویه قرار بده" to "شیشه ها حالت تهویه",
            "شیشه ها رو تو وضعیت تهویه قرار بده" to "شیشه ها حالت تهویه"
        )
        val recognizer = RahaCommandRecognizer()
        for ((spoken, canonical) in pairs) {
            val expected = recognizer.recognize(canonical)
            val actual = recognizer.recognize(spoken)
            assertTrue("Canonical: $canonical -> $expected", expected is Recognition.Matched)
            assertEquals("Spoken: $spoken", expected, actual)
        }
    }
    @Test fun automaticClimateWithDifferentWordOrders() {
        val recognizer = RahaCommandRecognizer()
        val expected = recognizer.recognize("تهویه خودکار")
        for (target in listOf("تهویه", "تهویه اتاق", "تهویه کابین", "تهویه ماشین")) {
            for (automatic in listOf("خودکار", "اتوماتیک", "اتومات")) {
                for (sentence in listOf("فعال سازی $target $automatic", "$target به شکل $automatic انجام بشه", "$target رو بصورت $automatic در بیار", "$target $automatic روشن کن")) {
                    assertEquals(sentence, expected, recognizer.recognize(sentence))
                }
            }
        }
    }
}
