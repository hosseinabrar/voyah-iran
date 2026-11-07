package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaRainSetpointTest {
    @Test fun explicitSetpointsHaveFourDistinctLevels() {
        val parser=OldRahaRecognizer()
        val values=mapOf("کم" to 1,"آرام" to 1,"آروم" to 1,"متوسط" to 2,"زیاد" to 3,"خیلی زیاد" to 4,"سریع" to 4,"تند" to 4,"یک" to 1,"دو" to 2,"سه" to 3,"چهار" to 4)
        for(noun in listOf("سنسور باران","سنسور بارون","برف پاک کن"))
            for(template in listOf("حساسیت $noun رو بذار روی %s","حساسیت $noun رو روی %s تنظیم کن","حساسیت $noun رو %s بذار"))
                for((value,level) in values) {
                    val text=template.format(value);val result=parser.recognize(text)
                    assertTrue("$text => $result",result is Recognition.Matched)
                    val c=(result as Recognition.Matched).command
                    assertEquals(text,"SET_WIPER_SENSITIVITY",c.action)
                    assertEquals(text,level,(c.parameters["level"] as Number).toInt())
                    assertFalse(c.parameters.containsKey("delta"))
                }
    }
    @Test fun invalidSetpointsNeverBecomeRelative() {
        val parser=OldRahaRecognizer()
        for(value in listOf("فلان چیز","پنج","صفر","کمتر","بیشتر","کم و زیاد")) {
            val text="حساسیت سنسور بارون رو بذار روی $value"
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
        }
    }
}
