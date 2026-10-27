package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaModeWordingTest {
    @Test fun spokenModeVariants() {
        val parser=RahaCommandRecognizer()
        for (word in listOf("تو مد", "تو مود", "روی مد", "روی مود", "تو حالت")) {
            check(parser, "ماشین رو بذار $word اقتصادی", "SET_DRIVE_MODE", "eco")
            check(parser, "چراغ ها رو بذار $word اتوماتیک", "SET_HEADLIGHT_MODE", "auto")
        }
        for(word in listOf("اتومات", "اتوماتیک", "تو اتومات"))
            check(parser, "ماشین رو بذار $word اقتصادی", "SET_DRIVE_MODE", "eco")
    }
    private fun check(parser:RahaCommandRecognizer, text:String, action:String, value:String) {
        val result=parser.recognize(text)
        assertTrue("$text: $result", result is Recognition.Matched)
        val command=(result as Recognition.Matched).command
        assertEquals(text,action,command.action)
        assertEquals(text,value,command.parameters["mode"])
    }
}
