package com.abrar.raha

import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class OldRahaRainSensorTest {
    @Test fun relativeAliasesWorkWithAndWithoutSavedExamples() {
        val parsers=listOf(OldRahaRecognizer { "".byteInputStream() },OldRahaRecognizer())
        val names=listOf("سنسور باران","سنسور بارون","برف پاک کن","برفپاککن","برف پاکن")
        val verbs=mapOf("زیاد کن" to 1,"کم کن" to -1,"بیشتر کن" to 1,"کمتر کن" to -1,"زیادتر کن" to 1,"تندتر کن" to 1,"کندتر کن" to -1,"تند تر کن" to 1,"کند تر کن" to -1)
        for(parser in parsers) for(name in names) for(prefix in listOf("حساسیت ",""))
            for(marker in listOf(" رو "," را "," ")) for((verb,delta) in verbs) {
                val text="$prefix$name$marker$verb"
                val result=parser.recognize(text)
                assertTrue("$text => $result", result is Recognition.Matched)
                val c=(result as Recognition.Matched).command
                assertEquals(text,"ADJUST_WIPER_SENSITIVITY",c.action)
                assertEquals(text,delta,(c.parameters["delta"] as Number).toInt())
                assertEquals(text,setOf("delta"),c.parameters.keys)
            }
    }
    @Test fun unrelatedAndContradictoryRequestsAreRejected() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(text in listOf("حساسیت سنسور بارون رو گرم کن","سنسور باران رو کم و زیاد کن","حساسیت سنسور باران رو کندتر و تندتر کن","بارون رو زیاد کن"))
            assertTrue("$text",parser.recognize(text) is Recognition.Unknown)
    }
    @Test fun absoluteSettingsRemainAbsolute() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(name in listOf("سنسور باران","سنسور بارون","برف پاک کن")) {
            val result=parser.recognize("حساسیت $name رو روی ۲ تنظیم کن") as Recognition.Matched
            assertEquals("SET_WIPER_SENSITIVITY",result.command.action)
            assertEquals(2,(result.command.parameters["level"] as Number).toInt())
        }
    }
}
