package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaSuspensionHeightTest {
    @Test fun threeHeightLevels() {
        val parser=RahaCommandRecognizer()
        val words=mapOf(
            "high" to listOf("بالا","زیاد کن","تا ته زیاد کن","حداکثر","ماکزیمم","بالاترین"),
            "medium" to listOf("متوسط","وسط","نرمال","استاندارد"),
            "low" to listOf("پایین","کم کن","تا ته کم کن","حداقل","مینیمم","پایین ترین"))
        for(noun in listOf("ارتفاع","ارتفاع ماشین","ارتفاع خودرو","ارتفاع تعلیق"))
            for((level,phrases) in words) for(phrase in phrases) {
                val input="$noun رو $phrase"
                val result=parser.recognize(input)
                assertTrue("$input => $result",result is Recognition.Matched)
                val cmd=(result as Recognition.Matched).command
                assertEquals("SET_SUSPENSION_HEIGHT",cmd.action)
                assertEquals(level,cmd.parameters["level"])
            }
    }
    @Test fun lowestIsNotAnAvailableLevel() {
        assertTrue(RahaCommandRecognizer().recognize("ارتفاع رو لوست کن") is Recognition.Unknown)
    }
}
