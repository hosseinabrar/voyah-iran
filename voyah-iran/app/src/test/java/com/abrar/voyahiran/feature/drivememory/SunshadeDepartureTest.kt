package com.abrar.voyahiran.feature.drivememory

import com.abrar.voyahiran.core.bridge.BridgePolicy
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.drivememory.domain.SunshadeDeparturePolicy
import com.abrar.voyahiran.feature.drivememory.data.SunshadeDepartureWriter
import org.junit.Assert.*
import org.junit.Test

class SunshadeDepartureTest {
    private fun SunshadeDeparturePolicy.observe(acc: Int?, speed: Int? = 0) = observe(acc, speed, true, true)

    @Test fun coldOffDoesNotClose() {
        val policy = SunshadeDeparturePolicy()
        repeat(5) { assertFalse(policy.observe(0)) }
    }
    @Test fun closesOncePerObservedPowerCycle() {
        val policy = SunshadeDeparturePolicy()
        repeat(3) {
            assertFalse(policy.observe(2))
            assertTrue(policy.observe(0))
            repeat(5) { assertFalse(policy.observe(0)) }
        }
    }
    @Test fun speedAndDoorLockWhilePoweredCannotTriggerClosure() {
        val policy = SunshadeDeparturePolicy()
        for (speed in listOf(0, 10, 0, 40, 0)) assertFalse(policy.observe(2, speed))
    }
    @Test fun unsafeOffIsConsumedNotDeferredUntilLaterStop() {
        for (speed in listOf(null, -1, 1, 80)) {
            val policy = SunshadeDeparturePolicy()
            policy.observe(2)
            assertFalse(policy.observe(0, speed))
            assertFalse(policy.observe(0, 0))
        }
    }
    @Test fun unknownPowerAndDisconnectDisarm() {
        val policy = SunshadeDeparturePolicy()
        for (unknown in listOf(null, -1, 1, 255)) {
            policy.observe(2)
            assertFalse(policy.observe(unknown))
            assertFalse(policy.observe(0))
        }
        policy.observe(2); policy.reset()
        assertFalse(policy.observe(0))
    }
    @Test fun enableWhileOffDoesNotMoveShade() {
        val policy = SunshadeDeparturePolicy()
        policy.reset(0)
        assertFalse(policy.observe(0))
        policy.reset(2)
        assertTrue(policy.observe(0))
    }
    @Test fun disabledOrNonVipCannotActOrReplayOnRenewal() {
        for ((enabled, vip) in listOf(false to true, true to false, false to false)) {
            val policy = SunshadeDeparturePolicy()
            policy.observe(2)
            assertFalse(policy.observe(0, 0, enabled, vip))
            assertFalse(policy.observe(0))
        }
    }
    @Test fun writerUsesOnlyBoundedCloseCommand() {
        var calls = 0
        SunshadeDepartureWriter(CommandRunner { command, _ ->
            calls++
            assertEquals(listOf("su", "0", "sunshade-departure", "close"), command)
            CommandResult(0, "SUNSHADE_CLOSED")
        }).close()
        assertEquals(1, calls)
        val command = listOf("sunshade-departure", "close")
        assertEquals(command, BridgePolicy.resolve(command))
        for (operation in listOf("open", "0", "retry")) assertNull(BridgePolicy.resolve(listOf("sunshade-departure", operation)))
        assertNull(BridgePolicy.resolve(command + "extra"))
    }
    @Test fun failureNeverRetries() {
        for (result in listOf(CommandResult(75, "Busy"), CommandResult(0, ""), CommandResult(0, "SUNSHADE_CLOSED", true))) {
            var calls = 0
            try {
                SunshadeDepartureWriter(CommandRunner { _, _ -> calls++; result }).close()
                fail("Must report failure")
            } catch (_: IllegalStateException) { }
            assertEquals(1, calls)
        }
    }
}
