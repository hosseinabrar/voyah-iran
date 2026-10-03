package com.abrar.voyahiran.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonebookSyncPolicyTest {
    @Test fun delayedOemPhonebookNeverExhaustsRetries() {
        repeat(20) { assertEquals(30_000L,PhonebookSyncPolicy.delay(true,false)) }
        assertEquals(300_000L,PhonebookSyncPolicy.delay(true,true))
    }
    @Test fun failureRetriesEvenWithOldContacts() {
        assertEquals(30_000L,PhonebookSyncPolicy.delay(false,true))
        assertEquals(30_000L,PhonebookSyncPolicy.delay(false,false))
    }
}
