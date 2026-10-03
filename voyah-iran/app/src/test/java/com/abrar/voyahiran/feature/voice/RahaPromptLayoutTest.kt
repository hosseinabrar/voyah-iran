package com.abrar.voyahiran.feature.voice

import com.abrar.raha.feedback.RahaPromptLayout
import org.junit.Assert.*
import org.junit.Test

class RahaPromptLayoutTest {
    @Test fun reservesQuestionPaddingAndLargeTouchTargetsWithoutOverflow() {
        assertEquals(3,RahaPromptLayout.buttonCount(360,64,24,72,8))
        assertEquals(4,RahaPromptLayout.buttonCount(480,64,24,72,8))
        assertEquals(4,RahaPromptLayout.buttonCount(2000,64,24,72,8))
        // A wrapped, taller question must reduce the list instead of clipping its last button.
        assertEquals(2,RahaPromptLayout.buttonCount(360,112,24,72,8))
        assertEquals(0,RahaPromptLayout.buttonCount(90,80,24,72,8))
        for(scale in listOf(1,2,3)) for(height in 120..900) {
            val count=RahaPromptLayout.buttonCount(height*scale,64*scale,24*scale,72*scale,8*scale)
            assertTrue(count in 0..4)
            assertTrue(64*scale+24*scale+count*80*scale<=height*scale)
        }
    }
}
