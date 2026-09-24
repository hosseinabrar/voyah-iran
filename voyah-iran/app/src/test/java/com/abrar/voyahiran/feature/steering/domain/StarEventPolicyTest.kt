package com.abrar.voyahiran.feature.steering.domain

import org.junit.Assert.*
import org.junit.Test

class StarEventPolicyTest {
    @Test fun shortPressReplacesOriginalActionWithOneBack() {
        val policy = StarEventPolicy(0x41)
        assertEquals(KeyRouting(true), policy.route(SteeringKeys.STAR, 0x1000))
        assertEquals(KeyRouting(true, listOf(RoutedKey(SteeringKeys.BACK, 0x2000))), policy.route(SteeringKeys.STAR, 0x2000))
        assertEquals(KeyRouting(true), policy.route(SteeringKeys.STAR, 0x10000))
    }

    @Test fun longHoldAndReleasePreserveOriginalActionWithoutBack() {
        val policy = StarEventPolicy(0x41)
        val actions = listOf(0x1000, 0x4000, 0x8000, 0x40000, 0x80000, 0x10000)
        val replayed = actions.flatMap { policy.route(SteeringKeys.STAR, it).events }
        assertEquals(actions.map { RoutedKey(0x41, it) }, replayed)
        for (event in replayed) {
            assertNotEquals(SteeringKeys.BACK, event.code)
        }
        // A firmware that also emits SHORT after LONG must not produce an extra Back.
        assertEquals(listOf(RoutedKey(0x41, 0x2000)), policy.route(SteeringKeys.STAR, 0x2000).events)
        policy.route(SteeringKeys.STAR, 0x1000)
        assertEquals(listOf(RoutedKey(SteeringKeys.BACK, 0x2000)), policy.route(SteeringKeys.STAR, 0x2000).events)
    }

    @Test fun nativeIdentityLongPressFallsThroughWithoutReinjectionLoop() {
        val policy = StarEventPolicy(SteeringKeys.STAR)
        assertFalse(policy.route(SteeringKeys.STAR, 0x8000).consume)
        assertFalse(policy.route(SteeringKeys.STAR, 0x10000).consume)
    }

    @Test fun unrelatedKeysAndCombinationsAreNotConsumed() {
        assertFalse(StarEventPolicy(0x41).route(0x80, 0x2000).consume)
        assertFalse(StarEventPolicy(0x41).route(SteeringKeys.BACK, 0x2000).consume)
        assertFalse(StarEventPolicy(0x41).route(0xAD, 0x8000).consume)
    }

    @Test fun doublePressRetainsOriginalMapping() {
        assertEquals(listOf(RoutedKey(0x41, 0x20000)), StarEventPolicy(0x41).route(SteeringKeys.STAR, 0x20000).events)
    }

    @Test fun releaseBeforeShortAndDuplicateShortStillProduceOnlyOneBack() {
        val policy = StarEventPolicy(0x41)
        val replayed = listOf(0x1000, 0x10000, 0x2000, 0x2000).flatMap { policy.route(SteeringKeys.STAR, it).events }
        assertEquals(listOf(RoutedKey(SteeringKeys.BACK, 0x2000)), replayed)
    }

    @Test fun backAlwaysTargetsCenterAcrossRepeatedDisplayTransfers() {
        val policy = StarEventPolicy(0x41)
        val payload = object : KeyPayload {}
        repeat(20) {
            for (display in listOf(0, 1, -1, 1, 0)) {
                policy.route(SteeringKeys.STAR, 0x1000, 128, display, payload)
                val back = policy.route(SteeringKeys.STAR, StarEventPolicy.SHORT, 128, display, payload)
                assertEquals(listOf(RoutedKey(SteeringKeys.BACK, StarEventPolicy.SHORT, display = 0)), back.events)
                assertTrue(back.consume)
                assertTrue(policy.route(SteeringKeys.STAR, StarEventPolicy.SHORT, 128, display, payload).events.isEmpty())
                policy.route(SteeringKeys.STAR, 0x10000, 128, display, payload)
            }
        }
    }

    @Test fun originalLongPressKeepsItsDisplayFlagsAndPayload() {
        val policy = StarEventPolicy(0x41)
        val payload = object : KeyPayload {}
        policy.route(SteeringKeys.STAR, 0x1000, 128, 1, payload)
        assertEquals(listOf(RoutedKey(0x41, 0x1000, 128, 1, payload),
            RoutedKey(0x41, 0x4000, 128, 1, payload)),
            policy.route(SteeringKeys.STAR, 0x4000, 128, 1, payload).events)
    }
}
