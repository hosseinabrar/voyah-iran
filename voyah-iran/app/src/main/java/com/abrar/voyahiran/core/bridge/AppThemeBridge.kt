package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner

internal object AppThemeBridge {
    private const val KEY = "voyah_iran_app_theme"
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized fun set(mode: String): CommandResult {
        require(mode in listOf("dark", "light", "vehicle"))
        fun read(key: String) = runner.run(listOf("/system/bin/settings", "get", "global", key), 3)
        val previous = read(KEY)
        if (previous.exitCode != 0 || previous.timedOut) return CommandResult(1, "APP_THEME_READ_FAILED")
        if (previous.output.trim() == mode && read(KEY + "_ack").output.trim() == mode)
            return CommandResult(0, "APP_THEME_APPLIED $mode")
        // Remove stale acknowledgements so an old framework cannot report success.
        val clear = runner.run(listOf("/system/bin/settings", "delete", "global", KEY + "_ack"), 3)
        if (clear.exitCode != 0 || clear.timedOut) return CommandResult(1, "APP_THEME_ACK_FAILED")
        val write = runner.run(listOf("/system/bin/settings", "put", "global", KEY, mode), 3)
        if (write.exitCode != 0 || write.timedOut) return CommandResult(1, "APP_THEME_WRITE_FAILED")
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            val ack = read(KEY + "_ack")
            if (ack.exitCode == 0 && !ack.timedOut && ack.output.trim() == mode) return CommandResult(0, "APP_THEME_APPLIED $mode")
            Thread.sleep(200)
        } while (SystemClock.elapsedRealtime() < deadline)
        val old = previous.output.trim()
        runner.run(if (old == "null" || old.isEmpty()) listOf("/system/bin/settings", "delete", "global", KEY)
            else listOf("/system/bin/settings", "put", "global", KEY, old), 3)
        return CommandResult(1, "APP_THEME_UPDATE_INSTALLER_AND_REBOOT")
    }
}
