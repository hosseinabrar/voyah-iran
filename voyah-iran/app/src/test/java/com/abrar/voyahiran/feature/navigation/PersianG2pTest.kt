package com.abrar.voyahiran.feature.navigation
import org.junit.Assert.*
import org.junit.Test
class PersianG2pTest {
    @Test fun upstreamParity() {
        val expected = mapOf(
            "ناهید" to "nahid",
            "بنفشه" to "banafshe",
            "زنبق" to "zonbagh",
            "حسابی" to "hesabi",
            "زلفی" to "zolfi",
            "گل" to "gol",
            "سکینه" to "sakine",
            "محمدی" to "mahmadi",
            "پور" to "poor",
            "عدالت" to "edalat",
            "توانا" to "tavana",
            "بنده" to "bande",
            "نواز" to "navaz",
            "حق" to "hagh",
            "طلب" to "talab",
            "البرز" to "alborz",
            "زاگرس" to "zagros",
            "مینایی" to "minayi",
            "بستنی" to "bastani",
            "همت" to "hamt",
            "مدیریت" to "modirit",
            "چمران" to "chamran",
            "وزرا" to "vozara",
            "سی" to "si",
            "ششم" to "sheshom",
            "گلها" to "golha",
            "نبی" to "nabi",
            "اکرم" to "akram"
        )
        for ((word, value) in expected) assertEquals(word, value, PersianG2p.predict(word))
    }
    @Test fun preservesKnownNamesAndUsesModelOnlyForUnknown() {
        assertEquals("hemmat modiriat mohammadipour", ClusterLatinText.convert("همت مدیریت محمدی پور"))
        assertEquals("zonbagh hesabi bastani", ClusterLatinText.convert("زنبق حسابی بستنی"))
        assertEquals("hagh talab", ClusterLatinText.convert("حق طلب"))
    }
    @Test fun boundedAndLiteralInputs() {
        for (word in listOf("abc", "۱۲۳", "بَستنی", "گل-ها", "ب".repeat(33))) assertNull(PersianG2p.predict(word))
        assertEquals("Chrome 123", ClusterLatinText.convert("Chrome 123"))
        repeat(5) { assertEquals("bastani", PersianG2p.predict("بستنی")) }
    }
}
