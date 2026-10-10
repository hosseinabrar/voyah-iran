package com.abrar.voyahiran.feature.apollo.data

import android.app.Application
import com.abrar.voyahiran.feature.apollo.domain.ApolloFeature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ApolloSelectionsTest {
    @Test fun savedEmergencySelectionsOverrideOppositeAndMissingFeedback() {
        val c = RuntimeEnvironment.getApplication()
        for (f in listOf(ApolloFeature.EMERGENCY_LANE, ApolloFeature.EMERGENCY_STEERING)) {
            ApolloSelections.save(c, f, true)
            assertEquals(true, ApolloSelections.display(c, f, false))
            assertEquals(true, ApolloSelections.display(c, f, null))
            ApolloSelections.save(c, f, false)
            assertEquals(false, ApolloSelections.display(c, f, true))
        }
    }
    @Test fun otherFeaturesContinueFollowingVehicleFeedback() {
        val c = RuntimeEnvironment.getApplication()
        ApolloSelections.save(c, ApolloFeature.TRAFFIC_LIGHT, true)
        assertEquals(false, ApolloSelections.display(c, ApolloFeature.TRAFFIC_LIGHT, false))
        assertNull(ApolloSelections.display(c, ApolloFeature.TRAFFIC_LIGHT, null))
    }
}
