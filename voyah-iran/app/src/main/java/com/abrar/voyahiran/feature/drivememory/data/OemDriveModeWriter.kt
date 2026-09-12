package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.drivememory.domain.validDriveMode

class OemDriveModeWriter(private val runner: CommandRunner) {
    fun checkAccess() {
        val result = runner.run(listOf("su", "0", "id", "-u"), 8)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "0") { "Root access required" }
    }
    fun apply(mode: Int) {
        val result = runner.run(listOf("su", "0") + arguments(mode), 8)
        check(!result.timedOut && result.exitCode == 0 && !result.output.contains("Error", true)
            && !result.output.contains("Exception", true)) { "OEM drive-mode request failed" }
        // am startservice is only a request; the engine waits for the CAN mode to match.
    }
    companion object {
        fun command(mode: Int): String {
            return arguments(mode).joinToString(" ")
        }
        fun arguments(mode: Int): List<String> {
            require(validDriveMode(mode))
            return listOf("am", "startservice", "--user", "0", "-n",
                "com.qinggan.app.vehiclesetting/com.qinggan.app.vehiclesetting.fragments.drivepreference.DriveModeIntentService",
                "-a", "com.qinggan.app.vehiclesetting.switch.DRIVE_MODE", "--ei", "drive_mode", mode.toString())
        }
    }
}
