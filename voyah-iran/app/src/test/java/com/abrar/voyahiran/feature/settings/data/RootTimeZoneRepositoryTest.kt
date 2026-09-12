package com.abrar.voyahiran.feature.settings.data

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.settings.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class RootTimeZoneRepositoryTest {
    private class FakeRunner(vararg results: CommandResult) : CommandRunner {
        val calls = mutableListOf<List<String>>()
        private val queue = ArrayDeque(results.toList())
        override fun run(arguments: List<String>, timeoutSeconds: Long): CommandResult {
            calls += arguments
            return queue.removeFirst()
        }
    }

    @Test fun fixedZoneHasCorrectSignAndNoSummerTime() {
        val zone = TimeZone.getTimeZone(TimeZoneMode.FIXED_IRAN.zoneId)
        for (date in listOf("2026-01-15T00:00:00Z", "2026-07-15T00:00:00Z")) {
            assertEquals(12_600_000, zone.getOffset(Instant.parse(date).toEpochMilli()))
        }
        assertFalse(zone.useDaylightTime())
    }

    @Test fun uncheckingSelectsTheRealTehranIdentifier() {
        assertEquals("Asia/Tehran", TimeZoneMode.forCorrection(false).zoneId)
        assertEquals("GMT+03:30", TimeZoneMode.forCorrection(true).zoneId)
    }

    @Test fun readDoesNotRequestRootOrWriteSettings() {
        val shell = FakeRunner(CommandResult(0, "ZONE=GMT+03:30\nAUTO=0"))
        val snapshot = RootTimeZoneRepository(shell).read()
        assertTrue(snapshot.correctionEnabled)
        assertEquals(false, snapshot.automatic)
        assertEquals("/system/bin/sh", shell.calls.single().first())
        assertFalse(shell.calls.single().last().contains("settings put"))
    }

    @Test fun unknownAutomaticSettingIsNotReportedAsDisabled() {
        val shell = FakeRunner(CommandResult(0, "ZONE=Asia/Tehran\nAUTO=null"))
        val snapshot = RootTimeZoneRepository(shell).read()
        assertFalse(snapshot.correctionEnabled)
        assertNull(snapshot.automatic)
    }

    @Test fun offsetEquivalentZoneDoesNotPretendCorrectionIsEnabled() {
        assertFalse(TimeZoneSnapshot("Asia/Tehran", false).correctionEnabled)
    }

    @Test fun emptyPropertyIsNotSilentlyReplacedWithGmt() {
        assertFailure(TimeZoneFailure.READ_FAILED) {
            RootTimeZoneRepository(FakeRunner(CommandResult(0, "ZONE=\nAUTO=0"))).read()
        }
    }

    @Test fun missingRootIsAnActionableFailure() {
        assertFailure(TimeZoneFailure.ROOT_UNAVAILABLE) {
            RootTimeZoneRepository(FakeRunner(CommandResult(127, "su: not found")))
                .setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun rootDenialIsNotReportedAsSuccess() {
        assertFailure(TimeZoneFailure.ROOT_UNAVAILABLE) {
            RootTimeZoneRepository(FakeRunner(CommandResult(1, "Permission denied")))
                .setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun timeoutIsDistinctFromRootDenial() {
        assertFailure(TimeZoneFailure.TIMEOUT) {
            RootTimeZoneRepository(FakeRunner(CommandResult(-1, "", true)))
                .setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun failedWriteIsNotReportedAsSuccess() {
        assertFailure(TimeZoneFailure.WRITE_FAILED) {
            RootTimeZoneRepository(FakeRunner(CommandResult(42, "setprop failed")))
                .setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun successfulExitWithoutCompletionMarkerIsRejected() {
        assertFailure(TimeZoneFailure.VERIFICATION_FAILED) {
            RootTimeZoneRepository(FakeRunner(CommandResult(0, "")))
                .setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun successfulCommandMustMatchActualSystemState() {
        assertFailure(TimeZoneFailure.VERIFICATION_FAILED) {
            RootTimeZoneRepository(FakeRunner(
                CommandResult(0, "VOYAH_TIMEZONE_OK"),
                CommandResult(0, "ZONE=Asia/Tehran\nAUTO=0"),
            )).setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun automaticTimezoneStillEnabledIsRejected() {
        assertFailure(TimeZoneFailure.VERIFICATION_FAILED) {
            RootTimeZoneRepository(FakeRunner(
                CommandResult(0, "VOYAH_TIMEZONE_OK"),
                CommandResult(0, "ZONE=GMT+03:30\nAUTO=1"),
            )).setMode(TimeZoneMode.FIXED_IRAN)
        }
    }

    @Test fun bothDirectionsAreVerifiedFromTheDevice() {
        for (mode in TimeZoneMode.entries) {
            val shell = FakeRunner(
                CommandResult(0, "Broadcast completed: result=0\nVOYAH_TIMEZONE_OK"),
                CommandResult(0, "ZONE=${mode.zoneId}\nAUTO=0"),
            )
            val actual = RootTimeZoneRepository(shell).setMode(mode)
            assertEquals(mode.zoneId, actual.zoneId)
            assertEquals(listOf("su", "0", "/system/bin/sh", "-c"), shell.calls.first().take(4))
            assertEquals(2, shell.calls.size)
        }
    }

    private fun assertFailure(expected: TimeZoneFailure, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (error: TimeZoneException) {
            assertEquals(expected, error.failure)
        }
    }
}
