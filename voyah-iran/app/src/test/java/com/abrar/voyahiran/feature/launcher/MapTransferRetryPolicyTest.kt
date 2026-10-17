package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class MapTransferRetryPolicyTest {
    private val busy="Bridge busy; no command executed"
    @Test fun retriesOnlyConfirmedPreDispatchBusy() {
        assertTrue(MapTransferRetryPolicy.shouldRetry(75,false,"$busy\n",1,20))
        assertFalse(MapTransferRetryPolicy.shouldRetry(0,false,busy,1,20))
        assertFalse(MapTransferRetryPolicy.shouldRetry(1,false,busy,1,20))
        assertFalse(MapTransferRetryPolicy.shouldRetry(75,true,busy,1,20))
        assertFalse(MapTransferRetryPolicy.shouldRetry(75,false,"Other busy operation",1,20))
    }
    @Test fun boundsAttemptsAndWaitTime() {
        assertTrue(MapTransferRetryPolicy.shouldRetry(75,false,busy,7,1849))
        assertFalse(MapTransferRetryPolicy.shouldRetry(75,false,busy,8,1000))
        assertFalse(MapTransferRetryPolicy.shouldRetry(75,false,busy,1,1850))
    }
}
