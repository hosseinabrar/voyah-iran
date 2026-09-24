package com.abrar.voyahiran.feature.navigation

import org.junit.Assert.*
import org.junit.Test

class PersianRoadWordsTest {
    @Test fun neighborhoodExamplesAndGeneralWords() {
        val cases = mapOf(
            "خیابان گلها" to "golha", "کوچه نبی اکرم" to "nabi akram",
            "گل‌ها" to "golha", "گل ها" to "golha", "گلهای بهشت" to "golhaye behesht",
            "نبی‌اکرم مرکزی" to "nabi akram", "نبِي اكرم" to "nabi akram",
            "أحمدآباد مركزى" to "ahmadabad",
            "بلوار پرستاران" to "parastaran", "کوچه نیلوفر" to "niloofar",
            "خیابان فرهنگ" to "farhang", "خیابان معلم" to "moallem",
            "کوچه شهید حمید کریمی" to "hamid karimi", "کوچه باغبان" to "baghban",
            "بلوار نرگس" to "narges", "کوچه باران" to "baran",
            "خیابان شقایق" to "shaghayegh", "میدان عدالت" to "meydan edalat",
            "کوچه مروارید" to "morvarid", "کوچه سوسن" to "soosan",
            "بلوار کشاورزان" to "keshavarzan", "گلها، ۱۲" to "golha 12",
            "خیابان نبی اکرم (مرکزی)" to "nabi akram",
            "تونل شهدا مرکزی" to "tunnel shohada"
        )
        for ((input, expected) in cases) assertEquals(input, expected, ClusterLatinText.convert(input))
    }
    @Test fun composesNamesNotEnumeratedInDictionary() {
        // These complete names are deliberately not stored as spelling exceptions.
        val cases = mapOf("احمدآباد" to "ahmadabad", "رحمت آباد" to "rahmatabad",
            "کریم‌آباد" to "karimabad", "حبیبپور" to "habibpour",
            "عزیززاده" to "azizzadeh", "امیدشهر" to "omidshahr",
            "گلنگار" to "golnegar", "گل‌افشان" to "gol afshan",
            "نسیمها" to "nasimha", "سروهای" to "sarvhaye", "ابر ها" to "abrha")
        for ((input, expected) in cases) {
            assertFalse(input, PersianRoadWords.words.containsKey(input))
            assertEquals(input, expected, ClusterLatinText.convert(input))
        }
    }
    @Test fun removesCentralAtBoundaryOnlyAndPreservesOldRules() {
        for (separator in listOf(" ", "\u200c", "  "))
            assertEquals("گلها", ClusterLatinText.shortRoadName("گلها${separator}مرکزی"))
        assertEquals("مرکزیان", ClusterLatinText.shortRoadName("خیابان مرکزیان"))
        assertEquals("shohada", ClusterLatinText.convert("خیابان شهدا مرکزی"))
        assertEquals("Destination", ClusterLatinText.convert("Destination"))
        assertEquals("si o sheshom", ClusterLatinText.convert("سی و ششم"))
        assertEquals("bazh", ClusterLatinText.convert("بَژ"))
    }
    @Test fun generalLexiconIsAsciiAndBounded() {
        assertTrue(PersianRoadWords.words.size > 600)
        for ((word, reading) in PersianRoadWords.words) {
            assertTrue(word, reading.matches(Regex("[a-z]+(?: [a-z]+)*")))
        }
        assertTrue(ClusterLatinText.convert("گلها ".repeat(100)).toByteArray().size <= 180)
    }
}
