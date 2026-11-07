package com.abrar.raha

import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class OldRahaClimateSyncTest {
    @Test fun syncParsesWithoutSavedExamples() {
        val parser = OldRahaRecognizer { "".byteInputStream() }
        for (text in listOf("یکسان سازی دمای چپ و راست", "دمای راست و چپ رو یکسان کن", "لطفا دمای راننده و همراه رو مثل هم کن", "همگام سازی دمای دو طرف رو فعال کن")) {
            val r = parser.recognize(text)
            assertTrue("$text => $r", r is Recognition.Matched)
            val c = (r as Recognition.Matched).command
            assertEquals("SET_FRONT_CLIMATE_SYNC", c.action)
            assertEquals(mapOf("enabled" to true), c.parameters)
        }
        val off = parser.recognize("یکسان سازی دمای چپ و راست رو خاموش کن") as Recognition.Matched
        assertEquals("SET_FRONT_CLIMATE_SYNC", off.command.action)
        assertEquals(mapOf("enabled" to false), off.command.parameters)
    }
    @Test fun conflictingAndUnrelatedRequestsDoNotEnableSync() {
        val parser = OldRahaRecognizer { "".byteInputStream() }
        for (text in listOf("دمای چپ و راست رو یکسان و خاموش و روشن کن", "نور چپ و راست رو یکسان کن", "دمای چپ و راست رو یکسان کن روی ۲۴")) {
            assertTrue(text, parser.recognize(text) is Recognition.Unknown)
        }
    }
}
