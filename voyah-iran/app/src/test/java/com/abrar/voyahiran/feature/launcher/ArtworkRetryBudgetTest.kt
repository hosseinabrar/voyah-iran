package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ArtworkRetryBudgetTest {
    @Test fun emptyMetadataUriDoesNotHideFallback(){
        assertEquals("content://cover/2",LauncherMedia.firstArtworkUri("", "content://cover/2", "content://cover/3"))
        assertEquals("content://cover/3",LauncherMedia.firstArtworkUri(null, "  ", "content://cover/3"))
        assertEquals("",LauncherMedia.firstArtworkUri(null,""))
    }
    @Test fun firstAttemptIsImmediateEvenAtBoot(){assertTrue(ArtworkRetryBudget().claim(0))}
    @Test fun positionCallbacksDoNotConsumeOrBypassBackoff(){
        val b=ArtworkRetryBudget();assertTrue(b.claim(100))
        for(t in 101L..5099L)assertFalse(b.claim(t))
        assertEquals(1,b.attempts);assertTrue(b.claim(5100))
    }
    @Test fun failureStopsAfterThreeAttempts(){
        val b=ArtworkRetryBudget()
        assertTrue(b.claim(0));assertTrue(b.claim(5500));assertTrue(b.claim(11000))
        assertTrue(b.exhausted);assertFalse(b.claim(100000))
    }
    @Test fun newTrackHasItsOwnBudget(){
        val old=ArtworkRetryBudget();repeat(3){old.claim(it*5500L)}
        assertTrue(old.exhausted);assertTrue(ArtworkRetryBudget().claim(16500))
    }
}
