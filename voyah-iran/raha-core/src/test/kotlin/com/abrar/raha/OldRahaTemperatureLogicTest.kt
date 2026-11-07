package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
class OldRahaTemperatureLogicTest {
    @Test fun unseenPoliteFormsKeepDirectionAmountAndZone() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(target in listOf("دمای اتاق","دمای کابین","دمای ماشین","دمای داخل ماشین","دمای خودرو"))
            for((operation,delta) in mapOf("ببر بالا" to 2,"بیار پایین" to -2,"ببر بالاتر" to 2,"بیار پایین تر" to -2))
                for(marker in listOf(" رو "," را "," ")) {
                    val text="لطفا $target${marker}دو درجه $operation برام"
                    val r=parser.recognize(text)
                    assertTrue("$text => $r",r is Recognition.Matched)
                    val c=(r as Recognition.Matched).command
                    assertEquals("ADJUST_CABIN_TEMPERATURE",c.action)
                    assertEquals(delta,(c.parameters["delta"] as Number).toInt());assertEquals("front",c.parameters["zone"])
                }
        val side=parser.recognize("دمای سمت شاگرد رو یک درجه بیار پایین") as Recognition.Matched
        assertEquals("passenger",side.command.parameters["zone"])
        for(t in listOf("دمای پیتزا رو بیار بالا","دمای ماشین رو بالا و پایین ببر"))assertTrue(t,parser.recognize(t) is Recognition.Unknown)
    }
    @Test fun exactDatabaseEntryWinsEvenForMidpointsAndSpeechRepair() {
        for(text in listOf("نورپردازی داخلی رو روی متوسط تنظیم کن","صدا رو کن کن","حساسیت سنسور بارون رو بذار روی کم")) {
            val key=Base64.getEncoder().encodeToString(OldRahaRecognizer.key(text).toByteArray())
            val parser=OldRahaRecognizer { "$key\tUNKNOWN\n".byteInputStream() }
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
            assertEquals("saved-example",parser.source)
        }
    }
}
