package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.dow.AmbientSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class DowAmbientBundleTest {
    @Test fun warningContainsAllFourCanControlsButNoAndroidPreference() {
        val b = DowAmbientBundle.create(DowAmbientBundle.warning()) { "OEM_$it" }
        assertEquals(4, b.size())
        assertEquals(2, b.getInt("OEM_SWITCH"))
        assertEquals(1, b.getInt("OEM_MUSICAL_RHYTHM_SWITCH"))
        assertEquals(62, b.getInt("OEM_COLOR"))
        assertEquals(10, b.getInt("OEM_BRIGHTNESS_LEVEL"))
    }
    @Test fun restorePreservesEveryCanValueForAllModes() {
        for (power in 1..2) for (music in 1..2) for (drive in 0..1) {
            val s = AmbientSnapshot(power, music, drive, 19, 7)
            val b = DowAmbientBundle.create(DowAmbientBundle.restore(s)) { it }
            assertEquals(4, b.size())
            assertEquals(power, b.getInt("SWITCH"))
            assertEquals(music, b.getInt("MUSICAL_RHYTHM_SWITCH"))
            assertEquals(19, b.getInt("COLOR"))
            assertEquals(7, b.getInt("BRIGHTNESS_LEVEL"))
        }
    }
    @Test fun pulsePreservesWarningModeAndRejectsUnsupportedFields() {
        val b = DowAmbientBundle.create(DowAmbientBundle.warning(10)) { it }
        assertEquals(4, b.size())
        assertEquals(10, b.getInt("BRIGHTNESS_LEVEL"))
        assertEquals(62, b.getInt("COLOR"))
        assertEquals(2, b.getInt("SWITCH"))
        assertEquals(1, b.getInt("MUSICAL_RHYTHM_SWITCH"))
        assertThrows(IllegalArgumentException::class.java) {
            DowAmbientBundle.create(mapOf("BRIGHTNESS_LEVEL" to 0)) { it }
        }
        assertThrows(IllegalStateException::class.java) {
            DowAmbientBundle.create(mapOf("drive" to 0)) { it }
        }
    }
    @Test fun offPhaseReallyPowersOffButKeepsFullBrightnessForNextOn() {
        val b = DowAmbientBundle.create(DowAmbientBundle.warning(0)) { it }
        assertEquals(1, b.getInt("SWITCH"))
        assertEquals(10, b.getInt("BRIGHTNESS_LEVEL"))
        assertEquals(62, b.getInt("COLOR"))
        assertThrows(IllegalArgumentException::class.java) { DowAmbientBundle.warning(5) }
    }
}
