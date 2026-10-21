package com.abrar.voyahiran.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import com.abrar.voyahiran.feature.navigation.NeshanCardDrivingPolicy.Action

class NeshanCardDrivingPolicyTest {
    @Test fun activeRouteAlwaysKeepsExistingRecovery() {
        for (driving in listOf(false, true)) for (follow in listOf(false, true))
            assertEquals(Action.ROUTE, NeshanCardDrivingPolicy.action(true, driving, follow))
    }
    @Test fun detachedFreeDrivingCameraFollowsWithoutDraggingOrOpeningMenu() {
        for (driving in listOf(false, true))
            assertEquals(Action.FOLLOW, NeshanCardDrivingPolicy.action(false, driving, true))
    }
    @Test fun freeDrivingWithNoFollowButtonNeedsNoAction() {
        assertEquals(Action.ALREADY_FOLLOWING, NeshanCardDrivingPolicy.action(false, true, false))
    }
    @Test fun idleMapUsesExistingMoreMenuEntry() {
        assertEquals(Action.ENTER_DRIVING, NeshanCardDrivingPolicy.action(false, false, false))
    }
}
