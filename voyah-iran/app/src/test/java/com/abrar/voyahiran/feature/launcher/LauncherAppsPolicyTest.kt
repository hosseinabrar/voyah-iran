package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherAppsPolicyTest {
    @Test fun excludesOnlyOemIdentifiers() {
        LauncherAppsPolicy.hidden.forEach { assertFalse(LauncherAppsPolicy.visible(it)) }
        listOf("Music","Seat","AC","APP_RDS","org.rajman.neshan.traffic.tehran.navigator").forEach {
            assertTrue(LauncherAppsPolicy.visible(it))
        }
    }
    @Test fun pageBoundaries() {
        assertEquals(1,LauncherAppsPolicy.pages(0))
        assertEquals(1,LauncherAppsPolicy.pages(27))
        assertEquals(2,LauncherAppsPolicy.pages(28))
        assertEquals(2,LauncherAppsPolicy.pages(54))
        assertEquals(3,LauncherAppsPolicy.pages(55))
    }
}
