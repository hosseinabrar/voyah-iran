package com.abrar.voyahiran.feature.drivememory.data

import com.abrar.voyahiran.core.shell.CommandRunner

class EnergyModeMemoryWriter(private val runner: CommandRunner) {
    fun apply(value: Int) {
        require(value in 1..4)
        val result = runner.run(listOf("su", "0", "energy-mode-memory", value.toString()), 8)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "ENERGY_MODE_CONFIRMED") {
            "ENERGY_MODE restore not confirmed"
        }
    }
}
