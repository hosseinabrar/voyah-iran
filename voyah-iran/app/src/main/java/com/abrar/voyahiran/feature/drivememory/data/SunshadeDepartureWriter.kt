package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandRunner

class SunshadeDepartureWriter(private val runner: CommandRunner) {
    fun close() {
        val result = runner.run(listOf("su", "0", "sunshade-departure", "close"), 10)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "SUNSHADE_CLOSED") {
            "Sunshade closure not confirmed; no automatic retry"
        }
    }
}
