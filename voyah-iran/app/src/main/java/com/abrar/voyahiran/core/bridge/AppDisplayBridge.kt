package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.util.UUID

/** Only bounded operations cross the authenticated bridge. */
object AppDisplayBridge {
    private const val KEY = "voyah_iran_display_policy"
    private const val ACK = "voyah_iran_display_policy_ack"
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized
    fun set(enabled: Boolean): CommandResult {
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.displayFrameworkHooks) return CommandResult(1, "DISPLAY_POLICY_UNSUPPORTED_PLATFORM")
        val nonce = (if (enabled) "1:" else "0:") + UUID.randomUUID().toString().replace("-", "")
        fun read(key: String) = runner.run(listOf("/system/bin/settings", "get", "global", key), 3)
        val previous = read(KEY)
        if (previous.exitCode != 0 || previous.timedOut) return CommandResult(1, "DISPLAY_POLICY_READ_FAILED")
        val result = runner.run(listOf("/system/bin/settings", "put", "global", KEY, nonce), 3)
        if (result.exitCode != 0 || result.timedOut) return CommandResult(1, "DISPLAY_POLICY_WRITE_FAILED")
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        do {
            val ack = read(ACK)
            if (ack.exitCode == 0 && ack.output.trim() == nonce) {
                AppLog.info("AppDisplay", "framework acknowledged enabled=$enabled left=145 top=50 displays=0,1")
                return CommandResult(0, "DISPLAY_POLICY_APPLIED enabled=$enabled")
            }
            Thread.sleep(300)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        val old = previous.output.trim()
        if (old == "null" || old.isEmpty()) runner.run(listOf("/system/bin/settings", "delete", "global", KEY), 3)
        else runner.run(listOf("/system/bin/settings", "put", "global", KEY, old), 3)
        AppLog.info("AppDisplay", "framework did not acknowledge; previous setting restored; update installer and reboot")
        return CommandResult(1, "DISPLAY_POLICY_NOT_ACKNOWLEDGED")
    }
}
