package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherFallbackPolicyTest {
    @Test fun missingEventDisplayOnH97cStillOpensCentralDrawer() {
        assertEquals(0, LauncherFallbackPolicy.targetDisplay(
            LauncherFallbackPolicy.buttonDisplay(null, null), false))
        assertEquals(1, LauncherFallbackPolicy.buttonDisplay(null, 1))
        assertEquals(1, LauncherFallbackPolicy.buttonDisplay(-1, 1))
        assertEquals(0, LauncherFallbackPolicy.buttonDisplay(0, 1))
        assertNull(LauncherFallbackPolicy.targetDisplay(
            LauncherFallbackPolicy.buttonDisplay(null, 1), true))
        assertNull(LauncherFallbackPolicy.targetDisplay(
            LauncherFallbackPolicy.buttonDisplay(2, null), false))
    }
    @Test fun onlySupportedVehicleVariantsUseCustomDrawer() {
        assertTrue(LauncherFallbackPolicy.requiresDrawer("h97a"))
        assertTrue(LauncherFallbackPolicy.requiresDrawer("Qinggan/H97C/h97c:9/release"))
        assertFalse(LauncherFallbackPolicy.requiresDrawer("h97b", "h97d"))
        assertFalse(LauncherFallbackPolicy.requiresDrawer("unknown", "h97a2"))
    }

    @Test fun menuStaysOnItsDisplay() {
        assertEquals(0, LauncherFallbackPolicy.targetDisplay(0, false))
        assertEquals(1, LauncherFallbackPolicy.targetDisplay(1, false))
        assertEquals(0, LauncherFallbackPolicy.targetDisplay(0, true))
        assertNull(LauncherFallbackPolicy.targetDisplay(1, true))
        assertNull(LauncherFallbackPolicy.targetDisplay(null, false))
        assertNull(LauncherFallbackPolicy.targetDisplay(2, false))
    }
    @Test fun onlyKnownCentralButtonIsInterceptedAfterFailure() {
        val pkg = LauncherFallbackPolicy.PACKAGE
        val id = "$pkg:id/screen_up_all_app_icon"
        assertTrue(LauncherFallbackPolicy.accepts(true, pkg, id))
        assertFalse(LauncherFallbackPolicy.accepts(false, pkg, id))
        assertFalse(LauncherFallbackPolicy.accepts(true, "other.app", id))
        assertFalse(LauncherFallbackPolicy.accepts(true, pkg, "$pkg:id/screen_up_home_icon"))
        assertFalse(LauncherFallbackPolicy.accepts(true, pkg, "$pkg:id/screen_down_all_app_icon"))
        assertFalse(LauncherFallbackPolicy.accepts(true, pkg, null))
    }
}
