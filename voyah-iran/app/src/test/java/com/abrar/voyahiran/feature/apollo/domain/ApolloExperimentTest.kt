package com.abrar.voyahiran.feature.apollo.domain

import org.junit.Assert.*
import org.junit.Test

class ApolloExperimentTest {
    @Test fun `OEM switch polarities are not interchangeable`() {
        assertEquals(2, ApolloExperiment.NAVIGATION.on)
        assertEquals(1, ApolloExperiment.NAVIGATION.off)
        assertEquals(1, ApolloExperiment.SPEED.on)
        assertEquals(2, ApolloExperiment.SPEED.off)
        assertEquals(2, ApolloExperiment.SPEED_WARNING.on)
        assertEquals(1, ApolloExperiment.SPEED_WARNING.off)
    }
    @Test fun `OEM modes include only confirmed values`() {
        assertEquals(setOf(2, 3, 4), ApolloExperiment.SPEED_MODE.choices.keys)
        assertFalse(ApolloExperiment.SPEED_MODE.accepts(1))
        assertEquals("خودکار", ApolloExperiment.SPEED_MODE.choices[2])
        assertEquals("دستی", ApolloExperiment.SPEED_MODE.choices[3])
        assertEquals("فقط نمایش محدودیت", ApolloExperiment.SPEED_MODE.choices[4])
        assertEquals(setOf(1, 2), ApolloExperiment.LANE_CONFIRM.choices.keys)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), ApolloExperiment.LANE_SCENARIOS.choices.keys)
    }
    @Test fun `unknown status and operation numbers cannot be written`() {
        ApolloExperiment.entries.forEach {
            assertFalse(it.accepts(-1)); assertFalse(it.accepts(0)); assertFalse(it.accepts(255))
            assertFalse(it.field.contains("FUNC_ENABLE"))
            assertFalse(it.field.endsWith("STATUS"))
        }
    }
    @Test fun `main section retains simplified order without emergency controls`() {
        assertEquals(listOf(ApolloFeature.LANE_CHANGE,
            ApolloFeature.TRAFFIC_LIGHT, ApolloFeature.LIGHT_CHANGE,
            ApolloFeature.SIGNS), ApolloFeature.displayed)
    }
}
