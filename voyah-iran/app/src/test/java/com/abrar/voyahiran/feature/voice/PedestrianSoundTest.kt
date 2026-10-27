package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class PedestrianSoundTest {
    @Test fun factoryValues() {
        for(enabled in listOf(true,false)) {
            val change=DriverAssistanceExecutor.plan("SET_PEDESTRIAN_WARNING_SOUND",mapOf("enabled" to enabled)).single()
            assertEquals("HUM_VSP_FUNCTION_SW",change.field)
            assertEquals(if(enabled)2 else 1,change.value)
            assertEquals(change.value,change.expected)
        }
    }
}
