package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object AppDensityBridge {
    private const val KEY = "voyah_iran_app_density"
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized fun set(mode: String): CommandResult {
        require(mode in listOf("120", "140", "160", "180", "200"))
        return write(KEY, mode)
    }
    @Synchronized fun setPackage(pkg: String, mode: String): CommandResult {
        require(AppDensityPreferences.validPackage(pkg))
        require(mode == "inherit" || mode.toIntOrNull() in AppDensityPreferences.values)
        val current = runner.run(listOf("/system/bin/settings", "get", "global", AppDensityPreferences.PACKAGES), 3)
        if (current.exitCode != 0 || current.timedOut) return CommandResult(1, "APP_DENSITY_READ_FAILED")
        val entries = AppDensityPreferences.parse(current.output.trim()).toMutableMap()
        if (mode == "inherit") entries.remove(pkg) else entries[pkg] = mode.toInt()
        return write(AppDensityPreferences.PACKAGES, AppDensityPreferences.encode(entries))
    }
    private fun write(key: String, mode: String): CommandResult {
        fun read(key: String) = runner.run(listOf("/system/bin/settings", "get", "global", key), 3)
        val previous = read(key)
        if (previous.exitCode != 0 || previous.timedOut) return CommandResult(1, "APP_DENSITY_READ_FAILED")
        if (previous.output.trim() == mode && read(key + "_ack").output.trim() == mode)
            return CommandResult(0, "APP_DENSITY_APPLIED $mode")
        // Remove stale acknowledgements so an old framework cannot report success.
        val clear = runner.run(listOf("/system/bin/settings", "delete", "global", key + "_ack"), 3)
        if (clear.exitCode != 0 || clear.timedOut) return CommandResult(1, "APP_DENSITY_ACK_FAILED")
        val write = runner.run(listOf("/system/bin/settings", "put", "global", key, mode), 3)
        if (write.exitCode != 0 || write.timedOut) return CommandResult(1, "APP_DENSITY_WRITE_FAILED")
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            val ack = read(key + "_ack")
            if (ack.exitCode == 0 && !ack.timedOut && ack.output.trim() == mode) return CommandResult(0, "APP_DENSITY_APPLIED $mode")
            Thread.sleep(200)
        } while (SystemClock.elapsedRealtime() < deadline)
        val old = previous.output.trim()
        runner.run(if (old == "null" || old.isEmpty()) listOf("/system/bin/settings", "delete", "global", key)
            else listOf("/system/bin/settings", "put", "global", key, old), 3)
        return CommandResult(1, "APP_DENSITY_UPDATE_INSTALLER_AND_REBOOT")
    }
}
