package com.abrar.voyahiran.feature.settings.data

import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.settings.domain.*

class RootTimeZoneRepository(private val runner: CommandRunner) : TimeZoneRepository {
    override fun read(): TimeZoneSnapshot {
        val result = runner.run(listOf("/system/bin/sh", "-c", TimeZoneCommands.READ), 5)
        if (result.timedOut || result.exitCode != 0) throw TimeZoneException(TimeZoneFailure.READ_FAILED)
        val lines = result.output.lineSequence().toList()
        val zone = lines.firstOrNull { it.startsWith("ZONE=") }?.removePrefix("ZONE=")?.trim()
        if (zone.isNullOrEmpty()) throw TimeZoneException(TimeZoneFailure.READ_FAILED)
        val automatic = when (lines.firstOrNull { it.startsWith("AUTO=") }?.removePrefix("AUTO=")?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
        return TimeZoneSnapshot(zone, automatic)
    }

    override fun setMode(mode: TimeZoneMode): TimeZoneSnapshot {
        // The vehicle's su accepts a numeric UID, not the desktop-style -c option.
        // ADB access does not imply this application is permitted to execute su.
        val result = runner.run(listOf("su", "0", "/system/bin/sh", "-c", TimeZoneCommands.apply(mode)), 25)
        if (result.timedOut) throw TimeZoneException(TimeZoneFailure.TIMEOUT)
        if (result.exitCode != 0) {
            val failure = when (result.exitCode) {
                40 -> TimeZoneFailure.READ_FAILED
                42 -> TimeZoneFailure.WRITE_FAILED
                43 -> TimeZoneFailure.VERIFICATION_FAILED
                else -> TimeZoneFailure.ROOT_UNAVAILABLE
            }
            throw TimeZoneException(failure, result.output)
        }
        if (!result.output.lineSequence().any { it.trim() == "VOYAH_TIMEZONE_OK" }) {
            throw TimeZoneException(TimeZoneFailure.VERIFICATION_FAILED)
        }
        return read().also {
            if (it.zoneId != mode.zoneId || it.automatic != false) {
                throw TimeZoneException(TimeZoneFailure.VERIFICATION_FAILED)
            }
        }
    }
}
