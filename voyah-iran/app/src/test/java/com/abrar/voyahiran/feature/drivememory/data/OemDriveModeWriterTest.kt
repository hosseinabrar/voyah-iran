package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import org.junit.Assert.*
import org.junit.Test

class OemDriveModeWriterTest {
    @Test fun commandUsesOfficialServiceAndOnlyValidatedInteger() {
        for (mode in 1..6) {
            val command = OemDriveModeWriter.command(mode)
            assertTrue(command.contains("DriveModeIntentService"))
            assertTrue(command.endsWith("--ei drive_mode $mode"))
        }
        for (mode in listOf(-1, 0, 7, Int.MAX_VALUE)) {
            try { OemDriveModeWriter.command(mode); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun serviceErrorIsNotSuccessEvenWithZeroExitCode() {
        val writer = OemDriveModeWriter(CommandRunner { _, _ -> CommandResult(0, "Error: Not found") })
        try { writer.apply(2); fail() } catch (_: IllegalStateException) { }
    }
    @Test fun rootDenialIsNotAccepted() {
        val writer = OemDriveModeWriter(CommandRunner { _, _ -> CommandResult(0, "2000") })
        try { writer.checkAccess(); fail() } catch (_: IllegalStateException) { }
    }
}
