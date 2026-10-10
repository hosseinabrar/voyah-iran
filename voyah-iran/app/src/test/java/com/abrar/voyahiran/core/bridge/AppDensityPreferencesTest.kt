package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class AppDensityPreferencesTest {
    @Test fun packagePreferencesRoundTripAndRejectInvalidEntries() {
        val values = mapOf("org.neshan.traffic.tehran" to 120, "com.apple.android.music" to 200)
        assertEquals(values, AppDensityPreferences.parse(AppDensityPreferences.encode(values)))
        assertTrue(AppDensityPreferences.parse(null).isEmpty())
        assertTrue(AppDensityPreferences.parse("test.app=999;bad=120;test.app;test.app=140=180").isEmpty())
        val remaining = values.toMutableMap().apply { remove("org.neshan.traffic.tehran") }
        assertEquals(mapOf("com.apple.android.music" to 200), AppDensityPreferences.parse(AppDensityPreferences.encode(remaining)))
        assertTrue(AppDensityPreferences.parse(AppDensityPreferences.encode(emptyMap())).isEmpty())
    }
    @Test fun bridgeOnlyAcceptsPackagesAndPresetsOrInheritance() {
        for (value in listOf("120", "140", "160", "200", "240", "inherit")) {
            val request = listOf("app-density-package", "test.player", value)
            assertEquals(request, BridgePolicy.resolve(request))
        }
        for (request in listOf(listOf("app-density-package", "test.player;reboot", "160"),
            listOf("app-density-package", "test.player", "999"), listOf("app-density-package", "test.player"),
            listOf("app-density-package", "test.player", "inherit", "extra"))) assertNull(BridgePolicy.resolve(request))
    }
}
