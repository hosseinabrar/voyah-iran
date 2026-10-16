package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult

/** VIP memory operation with a bounded, dedicated command. */
internal object EnergyModeMemoryBridge {
    fun apply(value: Int): CommandResult {
        if (!BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP subscription inactive")
        if (value !in 1..4) return CommandResult(126, "Invalid ENERGY_MODE state")
        return try {
            OemEnergyBinder.restoreMode(value)
            CommandResult(0, "ENERGY_MODE_CONFIRMED")
        } catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("EnergyModeMemory", "OEM restore failed; not replayed", error)
            CommandResult(1, "ENERGY_MODE restore not confirmed")
        }
    }
}
