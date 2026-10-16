package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandRunner

class SunshadeReadyWriter(private val runner: CommandRunner) {
    fun open() {
        val result = runner.run(listOf("su", "0", "sunshade-ready", "open"), 10)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "SUNSHADE_OPENED") {
            "Sunshade opening not confirmed; no automatic retry"
        }
    }
}
