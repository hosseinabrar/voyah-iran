package com.abrar.voyahiran.feature.recoverymemory.data

import com.abrar.voyahiran.core.shell.CommandRunner

/** Calls only Qinggan setVehicleState(HUM_ENERGY_PTREGEN_LEVL, level), never a raw CAN frame. */
class RecoveryLevelWriter(private val runner: CommandRunner) {
    fun apply(level: Int) {
        val result = runner.run(arguments(level), 8)
        check(!result.timedOut && result.exitCode == 0 && successfulReply.containsMatchIn(result.output)) {
            "Energy recovery request failed"
        }
        // A zero Binder exception code is transport acknowledgement, not vehicle confirmation.
    }

    companion object {
        private val successfulReply = Regex("Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000\\b")
        fun arguments(level: Int): List<String> {
            require(level in 2..4)
            // Transaction 0x3a: nullable VehicleState(ordinal 0x191, value 0x26b), then level.
            return listOf("su", "0", "service", "call", "qg.canbus", "58",
                "i32", "1", "i32", "401", "i32", "619", "i32", level.toString())
        }
    }
}
