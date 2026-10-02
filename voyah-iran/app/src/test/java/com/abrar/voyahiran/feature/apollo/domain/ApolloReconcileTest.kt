package com.abrar.voyahiran.feature.apollo.domain
import org.junit.Assert.*
import org.junit.Test
class ApolloReconcileTest {
 @Test fun enabledRawCacheIsNotRewritten() { assertFalse(ApolloReconcile.needsWrite(2, 1)) }
 @Test fun zeroTriggersActivation() { assertTrue(ApolloReconcile.needsWrite(2, 0)) }
 @Test fun expiredSubscriptionDisablesEnabledRawCache() { assertTrue(ApolloReconcile.needsWrite(1, 1)) }
 @Test fun disabledRawCacheIsNotRewritten() { assertFalse(ApolloReconcile.needsWrite(1, 0)) }
 @Test fun missingCacheResendsDesiredBundle() { assertTrue(ApolloReconcile.needsWrite(2, -1)); assertTrue(ApolloReconcile.needsWrite(1, -1)) }
 @Test fun unavailableFeedbackDoesNotTriggerWrites() { listOf(null, 2, 255).forEach { assertFalse(ApolloReconcile.needsWrite(2, it)); assertFalse(ApolloReconcile.needsWrite(1, it)) } }
 @Test(expected = IllegalArgumentException::class) fun invalidSetValueRejected() { ApolloReconcile.needsWrite(0, 0) }
}
