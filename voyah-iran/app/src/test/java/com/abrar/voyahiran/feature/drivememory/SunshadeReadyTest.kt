package com.abrar.voyahiran.feature.drivememory

import com.abrar.voyahiran.core.bridge.BridgePolicy
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.drivememory.domain.SunshadeReadyPolicy
import com.abrar.voyahiran.feature.drivememory.data.SunshadeReadyWriter
import org.junit.Assert.*
import org.junit.Test

class SunshadeReadyTest {
    private fun SunshadeReadyPolicy.observe(acc: Int?, ready: Int?, speed: Int? = 0) =
        observe(acc, ready, speed, true, true)

    @Test fun coldAlreadyReadyDoesNotMoveShade() {
        val policy = SunshadeReadyPolicy()
        repeat(5) { assertFalse(policy.observe(2, 1)) }
    }
    @Test fun accOnAloneDoesNotOpenAndReadyOpensOnce() {
        val policy = SunshadeReadyPolicy()
        assertFalse(policy.observe(0, 0))
        repeat(5) { assertFalse(policy.observe(2, 0)) }
        assertTrue(policy.observe(2, 1))
        repeat(5) { assertFalse(policy.observe(2, 1)) }
    }
    @Test fun offThenReadyWorksEvenWhenZeroReadyCallbackWasMissed() {
        val policy = SunshadeReadyPolicy()
        policy.observe(0, null)
        assertTrue(policy.observe(2, 1))
    }
    @Test fun nextPowerCycleRearmsWithoutReboot() {
        val policy = SunshadeReadyPolicy()
        repeat(3) {
            policy.observe(0, 0)
            assertTrue(policy.observe(2, 1))
        }
    }
    @Test fun noDeferredOpenWhenMovingOrSpeedUnknownAtReady() {
        for (speed in listOf(null, -1, 1, 40)) {
            val policy = SunshadeReadyPolicy()
            policy.observe(2, 0)
            assertFalse(policy.observe(2, 1, speed))
            assertFalse(policy.observe(2, 1, 0))
        }
    }
    @Test fun invalidReadyOrDisconnectDisarms() {
        for (ready in listOf(null, -1, 2, 255)) {
            val policy = SunshadeReadyPolicy()
            policy.observe(2, 0)
            assertFalse(policy.observe(2, ready))
            assertFalse(policy.observe(2, 1))
        }
        val policy = SunshadeReadyPolicy()
        policy.observe(2, 0); policy.reset()
        assertFalse(policy.observe(2, 1))
    }
    @Test fun enableWhileReadyDoesNotOpenButEnableBeforeReadyArms() {
        val policy = SunshadeReadyPolicy()
        policy.reset(1)
        assertFalse(policy.observe(2, 1))
        policy.reset(0)
        assertTrue(policy.observe(2, 1))
    }
    @Test fun disabledAndExpiredVipCannotOpenOrReplay() {
        for ((enabled, vip) in listOf(false to true, true to false, false to false)) {
            val policy = SunshadeReadyPolicy()
            policy.observe(2, 0)
            assertFalse(policy.observe(2, 1, 0, enabled, vip))
            assertFalse(policy.observe(2, 1))
        }
    }
    @Test fun writerAndAllowlistOnlyPermitOpen() {
        var count = 0
        SunshadeReadyWriter(CommandRunner { command, _ ->
            count++
            assertEquals(listOf("su", "0", "sunshade-ready", "open"), command)
            CommandResult(0, "SUNSHADE_OPENED")
        }).open()
        assertEquals(1, count)
        assertEquals(listOf("sunshade-ready", "open"), BridgePolicy.resolve(listOf("sunshade-ready", "open")))
        assertNull(BridgePolicy.resolve(listOf("sunshade-ready", "close")))
        assertNull(BridgePolicy.resolve(listOf("sunshade-ready", "open", "extra")))
    }
    @Test fun failuresDoNotRetry() {
        for (result in listOf(CommandResult(75, "Busy"), CommandResult(0, ""), CommandResult(0, "SUNSHADE_OPENED", true))) {
            var count = 0
            try {
                SunshadeReadyWriter(CommandRunner { _, _ -> count++; result }).open()
                fail()
            } catch (_: IllegalStateException) { }
            assertEquals(1, count)
        }
    }
}
