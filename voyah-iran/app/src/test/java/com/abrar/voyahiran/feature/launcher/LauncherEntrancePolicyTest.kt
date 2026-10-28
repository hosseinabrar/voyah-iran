package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherEntrancePolicyTest {
    @Test fun quickPanelKeepsPresentedContent() {
        assertTrue(LauncherEntrancePolicy.keepVisible(false,false,true,false))
    }
    @Test fun actualPauseStillHidesWithoutCamera() {
        assertFalse(LauncherEntrancePolicy.keepVisible(false,false,false,false))
    }
    @Test fun appOpenedFromPanelStillHides() {
        assertFalse(LauncherEntrancePolicy.keepVisible(false,true,true,false))
    }
    @Test fun coldLauncherStillEnters() {
        assertFalse(LauncherEntrancePolicy.keepVisible(true,false,true,false))
        assertFalse(LauncherEntrancePolicy.keepVisible(true,false,false,true))
    }
    @Test fun avmExceptionPreserved() {
        assertTrue(LauncherEntrancePolicy.keepVisible(false,false,false,true))
        assertFalse(LauncherEntrancePolicy.keepVisible(false,true,false,true))
    }
}
