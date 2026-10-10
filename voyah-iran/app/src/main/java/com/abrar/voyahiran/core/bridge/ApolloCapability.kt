package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

/** Identify Apollo by its installed OEM package, independent of VIN and SDB. */
internal object ApolloCapability {
    fun read(): CommandResult {
        val packages = ProcessCommandRunner(useBridge = false).run(
            listOf("/system/bin/pm", "list", "packages", "--user", "0", ApolloPackagePolicy.PACKAGE), 3
        )
        val available = ApolloPackagePolicy.detect(packages)
        if (available == null) {
            AppLog.info("Apollo", "equipment package query unavailable")
            return CommandResult(1, "APOLLO_CAPABILITY unknown")
        }
        AppLog.info("Apollo", "equipment source=package menuVisible=$available")
        return CommandResult(0, "APOLLO_CAPABILITY available=${if (available) 1 else 0}")
    }
}
