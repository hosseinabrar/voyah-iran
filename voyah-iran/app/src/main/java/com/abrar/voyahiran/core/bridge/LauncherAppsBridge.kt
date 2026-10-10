package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object LauncherAppsBridge {
    @Volatile var enabledForLegacy = false
    fun execute(args: List<String>): CommandResult {
        if (!BridgeSubscriptionSession.vipActive) return CommandResult(126, "VIP session required")
        // Only this authenticated, bounded channel can ask the OEM process to launch a tile.
        return ProcessCommandRunner(useBridge=false).run(listOf("/system/bin/settings", "put", "global",
            "voyah_iran_drawer_request", args.drop(1).joinToString(":")), 3)
    }
}
