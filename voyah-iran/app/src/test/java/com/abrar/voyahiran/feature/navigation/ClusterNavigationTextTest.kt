package com.abrar.voyahiran.feature.navigation

import org.junit.Assert.*
import org.junit.Test

class ClusterNavigationTextTest {
    @Test fun screenGuidanceUsesActualRoadDistanceAndDescription() {
        assertEquals(ClusterNavigationFrame("آزادی", 3, 250), ClusterNavigationText.visibleFrame("آزادی", "۲۵۰ متر", "به راست بپیچید"))
        assertEquals(ClusterNavigationFrame("۱٫۲ کیلومتر - آزادی"), ClusterNavigationText.visibleFrame("آزادی", "۱٫۲ کیلومتر", null))
        assertNull(ClusterNavigationText.visibleFrame(null, "۲۵۰ متر", "راست"))
        assertNull(ClusterNavigationText.visibleFrame("آزادی", null, "راست"))
    }
    @Test fun screenLeaseExpiresAndClears() {
        val value = ClusterNavigationFrame("آزادی")
        NeshanVisibleGuidance.update(value, 100)
        assertEquals(value, NeshanVisibleGuidance.current(2600))
        assertNull(NeshanVisibleGuidance.current(2601))
        NeshanVisibleGuidance.update(value, 2700)
        NeshanVisibleGuidance.clear()
        assertNull(NeshanVisibleGuidance.current(2701))
    }
    @Test fun ordinaryServiceNotificationIsNeverGuidance() {
        assertNull(ClusterNavigationText.parse(504311, "navigation", "نشان فعال است", null))
        assertNull(ClusterNavigationText.parse(17, null, "دانلود نقشه", null))
    }
    @Test fun knownNavigationAndCategoryAreAccepted() {
        assertEquals("۲۵۰ متر - آزادی", ClusterNavigationText.parse(1874524, null, "۲۵۰ متر - آزادی", null))
        assertEquals("راست بپیچید - آزادی", ClusterNavigationText.parse(15, "navigation", "راست بپیچید", "آزادی"))
    }
    @Test fun experimentalLeftAndPersianMeters() {
        assertEquals(ClusterNavigationFrame("آزادی", 2, 250), ClusterNavigationText.frame("۲۵۰ متر - آزادی", "common_ic_left"))
        assertEquals(ClusterNavigationFrame("آزادی", 3, 1200), ClusterNavigationText.frame("۱٫۲ کیلومتر - آزادی", "routing_module_ic_right"))
    }
    @Test fun unknownIconsAndDistancesUseTextNotInventedArrow() {
        assertEquals(0, ClusterNavigationText.frame("۲۵۰ متر - آزادی", "ic_neshan_notification").icon)
        assertEquals(0, ClusterNavigationText.frame("آزادی", "common_ic_left").icon)
        assertEquals(0, ClusterNavigationText.frame("۲۵۰ متر - آزادی", "common_ic_lane_straight_left").icon)
    }
    @Test fun utf8BoundPreservesCodepoints() {
        val result = ClusterNavigationText.limitUtf8("آزادی 😀".repeat(100), 180)
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 180)
        assertEquals(result, result.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
    @Test fun staleDataDoesNotBecomeFreshByPolling() {
        val state = NavigationFreshness()
        val value = ClusterNavigationFrame("آزادی")
        state.update("a", value, 0)
        assertEquals(value, state.current(1000))
        assertNull(state.current(15_001))
        state.update("a", value, 16_000)
        assertEquals(value, state.current(16_001))
    }
    @Test fun removingOtherNotificationDoesNotClearGuidance() {
        val state = NavigationFreshness()
        state.update("a", ClusterNavigationFrame("آزادی"), 100)
        state.remove("b"); assertNotNull(state.current(101))
        state.remove("a"); assertNull(state.current(101))
    }
    @Test fun oldNotificationSnapshotIsNotResurrected() {
        val state = NavigationFreshness()
        state.update("a", ClusterNavigationFrame("آزادی"), 30_000, 20_000)
        assertNull(state.current(30_000))
    }
}
