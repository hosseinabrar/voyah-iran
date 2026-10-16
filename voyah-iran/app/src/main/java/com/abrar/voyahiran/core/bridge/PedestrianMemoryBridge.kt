package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult

/** VIP memory operation with a bounded, dedicated command. */
internal object PedestrianMemoryBridge {
    fun apply(value: Int): CommandResult {
        if (!BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP subscription inactive")
        if (value !in 1..2) return CommandResult(126, "Invalid PEDESTRIAN state")
        return try {
            OemDriverAssistanceBinder.restorePedestrian(value)
            CommandResult(0, "PEDESTRIAN_CONFIRMED")
        } catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("PedestrianMemory", "OEM restore failed; not replayed", error)
            CommandResult(1, "PEDESTRIAN restore not confirmed")
        }
    }
}
