package com.abrar.raha.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RahaExecutionConfidenceTest {
    @Test fun matchesDisplayedHundred() {
        for (score in listOf(0.995, 0.996, 0.9999, 1.0))
            assertTrue(score.toString(), RahaExecutionConfidence.isDisplayedHundred(score))
        for (score in listOf(0.0, 0.65, 0.99, 0.9949, 0.99494))
            assertFalse(score.toString(), RahaExecutionConfidence.isDisplayedHundred(score))
    }
    @Test fun roundsToFourDecimalsBeforeDisplay() {
        assertTrue(RahaExecutionConfidence.isDisplayedHundred(0.99496))
    }
    @Test fun rejectsInvalidScores() {
        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1))
            assertFalse(RahaExecutionConfidence.isDisplayedHundred(score))
    }
}
