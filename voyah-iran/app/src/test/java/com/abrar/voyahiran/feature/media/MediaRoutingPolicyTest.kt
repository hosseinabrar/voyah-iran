package com.abrar.voyahiran.feature.media

import org.junit.Assert.*
import org.junit.Test

class MediaRoutingPolicyTest {
    @Test fun playingSessionWinsAndPausedPlayerIsRemembered() {
        val paused = MediaRoutingPolicy.Session("spotify", false, true)
        val playing = MediaRoutingPolicy.Session("apple", true, false)
        assertEquals("apple", MediaRoutingPolicy.choose(listOf(paused, playing), "spotify"))
        assertEquals("spotify", MediaRoutingPolicy.choose(listOf(playing.copy(playing=false, resumable=true), paused), "spotify"))
        assertEquals("spotify", MediaRoutingPolicy.choose(listOf(paused), "removed"))
        assertNull(MediaRoutingPolicy.choose(emptyList<MediaRoutingPolicy.Session<String>>(), "removed"))
        assertNull(MediaRoutingPolicy.choose(listOf(paused.copy(resumable=false)), "spotify"))
    }
    @Test fun completePressSendsExactlyOneCommandToLatchedPlayer() {
        val policy = MediaPressPolicy<String>()
        assertEquals(MediaPressPolicy.Result<String>(true), policy.route(6, 0x1000, "spotify"))
        assertEquals(MediaPressPolicy.Result(true, "spotify"), policy.route(6, 0x2000, "apple"))
        assertNull(policy.route(6, 0x2000, "apple").target)
        assertNull(policy.route(6, 0x10000, "apple").target)
        policy.route(6, 0x1000, "apple")
        assertEquals("apple", policy.route(6, 0x2000, "apple").target)
    }
    @Test fun noSessionAndOtherKeysPassThrough() {
        val policy = MediaPressPolicy<String>()
        assertFalse(policy.route(3, 0x1000, null).consumed)
        assertFalse(policy.route(3, 0x2000, "appearedMidPress").consumed)
        assertFalse(policy.route(130, 0x2000, "spotify").consumed)
        assertNull(MediaRoutingPolicy.androidKey(130))
        assertEquals(listOf(87,88,85,126,127), MediaRoutingPolicy.keys.map(MediaRoutingPolicy::androidKey))
        policy.reset()
        assertEquals("spotify", policy.route(3, 0x2000, "spotify").target)
    }
}
