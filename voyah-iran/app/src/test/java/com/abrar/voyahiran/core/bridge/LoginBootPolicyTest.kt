package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class LoginBootPolicyTest {
    @Test fun expiryOrHeartbeatLossDoesNotRestoreQrDuringAcceptedBoot() {
        val boot=LoginBootPolicy(LoginBootPolicy.cached("100:200"))
        boot.update(false)
        assertEquals(true,boot.allowed)
        val nextBoot=LoginBootPolicy(LoginBootPolicy.cached("300:300"))
        assertEquals(false,nextBoot.allowed)
    }
    @Test fun missingAndMalformedStateIsUnknownRatherThanDenial() {
        for(value in listOf(null,"","abc","1","3:2","1:2:3"))assertNull(LoginBootPolicy.cached(value))
        val boot=LoginBootPolicy(null)
        assertNull(boot.allowed)
        boot.update(false);assertEquals(false,boot.allowed)
        boot.update(true);assertEquals(true,boot.allowed)
        boot.update(false);assertEquals(true,boot.allowed)
    }
}
