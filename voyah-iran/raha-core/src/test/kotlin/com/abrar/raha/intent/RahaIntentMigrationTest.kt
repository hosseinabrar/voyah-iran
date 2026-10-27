package com.abrar.raha.intent

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import java.io.File

class RahaIntentMigrationTest {
    @Test fun groundingMatchesPythonWithoutAnyDirectionOverride() {
        val rows=JSONArray(checkNotNull(javaClass.getResourceAsStream("/intent-grounding-parity.json")).bufferedReader().use { it.readText() })
        fun map(obj:org.json.JSONObject):Map<String,Any> = obj.keys().asSequence().associateWith { obj.get(it) }
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val text=row.getString("text")
            val permitted=row.getJSONObject("allowed")
            val allowed=permitted.keys().asSequence().associateWith { key -> permitted.getJSONArray(key).let { a -> (0 until a.length()).map { a.get(it) } } }
            val result=RahaIntentGrounding.apply(text,row.getString("action"),map(row.getJSONObject("parameters")),allowed)
            assertEquals(text,row.getBoolean("rejected"),result.error!=null)
            val keys=row.getJSONArray("grounded")
            assertEquals(text,(0 until keys.length()).map { keys.getString(it) }.toSet(),result.grounded)
            for((key,value) in map(row.getJSONObject("expected"))) {
                val actual=result.parameters[key]
                if(value is Number && actual is Number)assertEquals("$text / $key",value.toDouble(),actual.toDouble(),0.0)
                else assertEquals("$text / $key",value,actual)
            }
        }
    }
    private fun asset(name:String)=File("../raha-runtime/src/main/assets/raha-intent",name)
    @Test fun tokenizerMatchesTrainedModel() {
        val tokenizer=RahaWordPiece(asset("vocab.txt").readLines())
        val fixtures=JSONArray(checkNotNull(javaClass.getResourceAsStream("/intent-tokenizer-parity.json")).bufferedReader().use { it.readText() })
        for(i in 0 until fixtures.length()) {
            val row=fixtures.getJSONObject(i);val expected=row.getJSONArray("ids")
            assertArrayEquals(row.getString("text"),LongArray(expected.length()){expected.getLong(it)},tokenizer.encode(row.getString("text")).ids)
        }
    }
    @Test fun numbersAreExactButDirectionsStayWithModel() {
        val allowed=mapOf("delta" to (-16..16).filter { it!=0 }.map { it as Any },"zone" to listOf<Any>("driver","passenger","front"))
        for((word,n) in listOf("سه" to 3,"شیش" to 6,"پانزده" to 15,"۶" to 6,"یکی" to 1)) {
            val result=RahaIntentGrounding.apply("دمای شاگرد رو $word درجه کم کن","ADJUST_CABIN_TEMPERATURE",mapOf("delta" to -2,"zone" to "driver"),allowed)
            assertNull(result.error)
            assertEquals(-n,result.parameters["delta"])
            assertEquals("driver",result.parameters["zone"])
            assertEquals(setOf("delta"),result.grounded)
        }
        val result=RahaIntentGrounding.apply("گرمکن صندلی همراه رو روشن کن","SET_SEAT_HEATING",mapOf("enabled" to true,"seat" to "driver"),emptyMap())
        assertEquals("driver",result.parameters["seat"])
        assertTrue(result.grounded.isEmpty())
    }
    @Test fun invalidNumbersAreRejectedNotClampedOrGuessed() {
        val result=RahaIntentGrounding.apply("فن رو بذار روی هشت","SET_FAN_SPEED",mapOf("level" to 6,"zone" to "front"),mapOf("level" to (0..7).map { it as Any }))
        assertNotNull(result.error)
        assertTrue(result.grounded.isEmpty())
        assertNotNull(RahaIntentGrounding.apply("دمای کابین رو سه یا شش درجه زیاد کن","ADJUST_CABIN_TEMPERATURE",mapOf("delta" to 3),mapOf("delta" to listOf<Any>(3,6))).error)
    }
    @Test fun contactWordOrderAndLearnedShell() {
        for(name in listOf("علی","مریم","محمد رضایی"))for(sentence in listOf("یه زنگ به $name بزن","زنگ بزن به $name","با $name تماس بگیر","شماره $name رو بگیر"))
            assertEquals(name,RahaContactSpan.extract(sentence,emptyList()))
        assertEquals("علی",RahaContactSpan.extract("وصل شو به علی لطفا",listOf("وصل شو به " to " لطفا")))
        assertNull(RahaContactSpan.extract("یه زنگ بزن",emptyList()))
    }
    @Test fun halfSpacesAreNormalizedBeforeTokenization() {
        assertEquals("گرم کن صندلی",RahaWordPiece.normalize(" گرم\u200cکن  صندلي "))
    }
    @Test fun unicodeWhitespaceAndPersianWordBoundariesRemainSupported() {
        assertEquals("دمای کابین سه",RahaWordPiece.normalize("دمای\u00a0کابین\u0085\u202fسه"))
        assertEquals("بهزاد",RahaContactSpan.extract("یه زنگ به بهزاد بزن",emptyList()))
        assertEquals("رویا",RahaContactSpan.extract("به رویا زنگ بزن",emptyList()))
        assertEquals(15.0,RahaIntentGrounding.quantities("صدا رو روی ۱۵ بذار").single().value,0.0)
    }
    @Test fun productionPatternsDoNotUseUnsupportedAndroidUnicodeFlag() {
        for(root in listOf(File("src/main"),File("../raha-runtime/src/main/java"))) {
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt","java") }.forEach {
                assertFalse("Unsupported Android regex flag in $it",it.readText().contains("(?U)"))
            }
        }
    }
}
