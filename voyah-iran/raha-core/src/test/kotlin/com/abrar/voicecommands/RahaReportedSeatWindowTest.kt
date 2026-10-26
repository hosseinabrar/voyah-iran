package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaReportedSeatWindowTest {
    private val parser=RahaCommandRecognizer()
    @Test fun reportedSeatOff() {
        val result=parser.recognize("رها گرمکن صندلی رو خاموش کن")
        assertTrue("$result",result is Recognition.Matched)
        val command=(result as Recognition.Matched).command
        assertEquals("SET_SEAT_HEATING",command.action)
        assertEquals(mapOf("seat" to "driver","enabled" to false,"level" to 0),command.parameters)
    }
    @Test fun reportedHalfWindows() {
        for(verb in listOf("بده پایین","بکش پایین")) {
            val result=parser.recognize("رها شیشه رو تا نصفه $verb")
            assertTrue("$verb: $result",result is Recognition.Matched)
            val command=(result as Recognition.Matched).command
            assertEquals("SET_WINDOW_POSITION",command.action)
            assertEquals(mapOf("window" to "driver","percent" to 50),command.parameters)
        }
    }
    @Test fun reportedPhrasesAreAvailableToSpeechDecoder() {
        val phrases=com.abrar.raha.RahaSpeechVocabulary.phrases()
        for(text in listOf("گرمکن صندلی رو خاموش کن","شیشه رو تا نصفه بده پایین","شیشه رو تا نصفه بکش پایین"))
            assertTrue(text,text in phrases)
    }
}
