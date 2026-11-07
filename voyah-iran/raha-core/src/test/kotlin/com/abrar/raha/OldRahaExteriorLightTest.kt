package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaExteriorLightTest {
    @Test fun aliasesWorkWithoutMemorizedExamples() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(noun in listOf("چراغای بیرون","چراغ های بیرونی ماشین","چراغ جلوی خودرو","چراغهای خارجی","چراغ های بیرونی جلو","چراغ ماشین"))
            for((phrase,mode) in mapOf("خاموشش کن" to "off","روشن کن" to "lowBeam","بذار روی اتوماتیک" to "auto","بذار رو نور پایین" to "lowBeam","بذار روی کوچک" to "position","بذار روی دی لایت" to "position")) {
                val text="لطفا $noun را $phrase برام"
                val r=parser.recognize(text)
                assertTrue("$text => $r",r is Recognition.Matched)
                val c=(r as Recognition.Matched).command
                assertEquals("SET_HEADLIGHT_MODE",c.action);assertEquals(mode,c.parameters["mode"])
            }
    }
    @Test fun interiorAndInvalidRequestsAreNotMappedToExterior() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(text in listOf("چراغای داخل رو روشن کن","چراغ های داخلی رو خاموش کن")) {
            val r=parser.recognize(text) as Recognition.Matched
            assertEquals("SET_AMBIENT_LIGHT_POWER",r.command.action)
        }
        for(text in listOf("چراغ بیرونی رو گرم کن","چراغ بیرونی رو با موزیک هماهنگ کن","چراغ داخل بیرون رو روشن کن"))
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
    }
}
