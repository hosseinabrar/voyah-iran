package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandRunner

class AebMemoryWriter(private val runner: CommandRunner) {
    fun apply(value: Int) {
        require(value in 1..2)
        val result = runner.run(listOf("su", "0", "aeb-memory", value.toString()), 8)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "AEB_CONFIRMED") {
            "AEB restore not confirmed"
        }
    }
}
