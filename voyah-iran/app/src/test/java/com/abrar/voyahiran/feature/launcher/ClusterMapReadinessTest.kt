package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMapReadinessTest {
    @Test fun bridgeRestartRequiresNewAnnouncement() {
        val state=ClusterMapReadiness()
        state.reply(state.generation,true,true)
        assertTrue(state.acknowledged)
        state.reply(state.generation,true,false)
        assertFalse(state.acknowledged)
        state.reply(state.generation,true,true)
        assertTrue(state.acknowledged)
    }

    @Test fun replacedSurfaceRejectsInFlightAcknowledgement() {
        val state=ClusterMapReadiness()
        val old=state.generation
        state.invalidate()
        state.reply(old,true,true)
        assertFalse(state.acknowledged)
        state.reply(state.generation,false,true)
        assertFalse(state.acknowledged)
        state.reply(state.generation,true,true)
        assertTrue(state.acknowledged)
        state.invalidate()
        assertFalse(state.acknowledged)
    }
}
