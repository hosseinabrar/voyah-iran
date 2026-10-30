package com.abrar.voyahiran.feature.steering.domain

import org.junit.Assert.*
import org.junit.Test

class StarBackTransportTest {
    @Test fun legacyShortBackUsesCenterEvenAfterDisplayChanges() {
        val policy = StarEventPolicy(0x41)
        for (display in listOf(0, 1, 0, 1, 0)) {
            policy.route(SteeringKeys.STAR, 0x1000, display = display)
            val event = policy.route(SteeringKeys.STAR, StarEventPolicy.SHORT, display = display).events.single()
            assertEquals(0, event.display)
            assertTrue(StarBackTransport.directCenter(28, event))
            assertTrue(StarBackTransport.directCenter(30, event))
            assertTrue(policy.route(SteeringKeys.STAR, StarEventPolicy.SHORT, display = display).events.isEmpty())
        }
    }

    @Test fun originalLongPressAndOtherKeysStayOnOemPath() {
        val back = RoutedKey(SteeringKeys.BACK, StarEventPolicy.SHORT)
        for (event in listOf(back.copy(action = 0x8000), back.copy(action = 0x10000),
            back.copy(code = 0x41), back.copy(display = 1), back.copy(flags = 1),
            back.copy(payload = object : KeyPayload {}))) {
            assertFalse(StarBackTransport.directCenter(28, event))
        }
        assertFalse(StarBackTransport.directCenter(29, back))
    }

    @Test fun checksOnlyTheMappingsThatWillActuallyBeReplayed() {
        assertEquals(listOf(0x41), StarBackTransport.replayTargets(28, 0x41))
        assertEquals(listOf(0x41), StarBackTransport.replayTargets(30, 0x41))
        assertEquals(listOf(SteeringKeys.BACK), StarBackTransport.replayTargets(28, SteeringKeys.BACK))
        assertTrue(StarBackTransport.replayTargets(28, SteeringKeys.STAR).isEmpty())
    }

    @Test fun emitsOneDownAndOneUp() {
        val actions = mutableListOf<Int>()
        assertTrue(StarBackTransport.press { actions += it; true })
        assertEquals(listOf(0, 1), actions)
    }

    @Test fun rejectionDoesNotRetryAndStillReleasesKey() {
        val actions = mutableListOf<Int>()
        assertFalse(StarBackTransport.press { actions += it; it == 1 })
        assertEquals(listOf(0, 1), actions)
        assertFalse(StarBackTransport.press { it == 0 })
    }

    @Test fun uncertainDownStillReleasesWithoutRepeatingDown() {
        val actions = mutableListOf<Int>()
        try {
            StarBackTransport.press { actions += it; if (it == 0) error("lost result"); true }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf(0, 1), actions)
    }
}
