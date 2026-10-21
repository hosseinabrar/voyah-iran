package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.launcher.LauncherWindowTheme
import org.junit.Assert.*
import org.junit.Test

class LauncherBackdropTest {
    @Test fun onlyTwoPhysicalDisplaysAreEligible() {
        val color = 0xffe5ebf3.toInt()
        assertNotNull(BackdropSpec.forDisplay(0, 1, 0, 1920, 720, color))
        assertNotNull(BackdropSpec.forDisplay(1, 2, 7, 1920, 720, color))
        assertNull(BackdropSpec.forDisplay(2, 1, 2, 1920, 720, color))
        assertNull(BackdropSpec.forDisplay(1, 5, 7, 1920, 720, color))
        assertNull(BackdropSpec.forDisplay(0, 1, -1, 1920, 720, color))
        assertNull(BackdropSpec.forDisplay(0, 1, 0, 0, 720, color))
        assertNull(BackdropSpec.forDisplay(1, 2, 1, 1920, 0, color))
        assertEquals(Int.MIN_VALUE, BackdropSpec.Z)
    }
    @Test fun colorMatchesLauncherAndOnlyGeometryOrThemeChangesSpecification() {
        val light = BackdropSpec(4, 1920, 720, LauncherWindowTheme.background(true))
        assertArrayEquals(floatArrayOf(229/255f,235/255f,243/255f),light.rgb(),0.00001f)
        val dark = light.copy(color=LauncherWindowTheme.background(false))
        assertArrayEquals(floatArrayOf(0f,0f,0f),dark.rgb(),0f)
        assertEquals(light,BackdropSpec.forDisplay(1,1,4,1920,720,light.color))
        assertNotEquals(light,dark)
        assertNotEquals(light,light.copy(width=2560))
        assertNotEquals(light,light.copy(stack=8))
    }
}
