package com.abrar.voyahiran.feature.voice

import com.abrar.raha.RahaCommandRecognizer
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class PedestrianSoundTest {
    @Test fun phrasesAndFactoryValues() {
        val parser = RahaCommandRecognizer()
        val targets = listOf("هشدار عابر پیاده", "صدای هشدار عابر", "صدای عابر", "صدای عابر پیاده",
            "صدای هشدار برای عابر پیاده", "آلارم عابر", "هشدار صوتی عابر")
        for (target in targets) for ((verb, enabled) in listOf("رو فعال کن" to true, "رو روشن کن" to true,
            "رو غیر فعال کن" to false, "رو خاموش کن" to false, "را غیرفعال کنید" to false)) {
            val phrase = "$target $verb"
            val result = parser.recognize(phrase)
            assertTrue("$phrase => $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(phrase, "SET_PEDESTRIAN_WARNING_SOUND", command.action)
            assertEquals(mapOf("enabled" to enabled), command.parameters)
            val change = DriverAssistanceExecutor.plan(command.action, command.parameters).single()
            assertEquals("HUM_VSP_FUNCTION_SW", change.field)
            assertEquals(if (enabled) 2 else 1, change.value)
            assertEquals(change.value, change.expected)
        }
    }
    @Test fun ordinaryAudioStillTargetsAudio() {
        val result = RahaCommandRecognizer().recognize("صدا رو زیاد کن") as Recognition.Matched
        assertEquals("ADJUST_AUDIO_VOLUME", result.command.action)
    }
}
