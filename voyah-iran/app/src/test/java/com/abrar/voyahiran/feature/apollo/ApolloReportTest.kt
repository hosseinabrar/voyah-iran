package com.abrar.voyahiran.feature.apollo

import com.abrar.voyahiran.feature.apollo.domain.*
import org.junit.Assert.*
import org.junit.Test

class ApolloReportTest {
    @Test fun missingDataIsUnknownNotInactive() {
        assertNull(subscriptionReport(null, null))
        assertNull(subscriptionReport("1", ""))
        for (feature in ApolloFeature.entries) {
            assertNull(feature.checked(null)); assertNull(feature.checked(10)); assertNull(feature.checked(255))
        }
    }
    @Test fun expiredSubscriptionIsNotActive() {
        assertEquals(true, subscriptionReport("1", "0"))
        assertEquals(false, subscriptionReport("1", "1"))
        assertEquals(false, subscriptionReport("0", "0"))
    }
    @Test fun featurePolaritiesAreNotAssumedEqual() {
        assertEquals(true, ApolloFeature.LANE_CHANGE.checked(2))
        assertEquals(false, ApolloFeature.LANE_CHANGE.checked(1))
        assertEquals(true, ApolloFeature.EMERGENCY_STEERING.checked(1))
        assertEquals(false, ApolloFeature.EMERGENCY_STEERING.checked(2))
    }
    @Test fun featureReportRemainsIndependentOfSubscription() {
        val report = ApolloReport(null, mapOf(ApolloFeature.LANE_CHANGE to true))
        assertNull(report.subscribed)
        assertEquals(true, report.features[ApolloFeature.LANE_CHANGE])
    }
}
