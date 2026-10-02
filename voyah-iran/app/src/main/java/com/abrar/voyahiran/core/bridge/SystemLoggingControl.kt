package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File

/** Root-only, reversible logd control. Never modifies OEM init files or SELinux. */
internal object SystemLoggingControl {
    private val saved = File("/data/local/tmp/voyahiran-bridge/logging-disabled")
    private val runner = ProcessCommandRunner(useBridge = false)
    @Synchronized fun enabled() = saved.exists()
    @Synchronized fun whileEnabled(action: () -> Unit) { if (saved.exists()) action() }
    private fun state(): String {
        val result = runner.run(listOf("/system/bin/getprop", "init.svc.logd"), 3)
        check(result.exitCode == 0 && !result.timedOut) { "Cannot read logd state" }
        return result.output.trim()
    }
    private fun change(running: Boolean) {
        val result = runner.run(listOf("/system/bin/setprop", if (running) "ctl.start" else "ctl.stop", "logd"), 3)
        check(result.exitCode == 0 && !result.timedOut) { "Cannot change logd state: ${result.output}" }
        repeat(30) {
            if (state() == if (running) "running" else "stopped") return
            Thread.sleep(100)
        }
        error("logd state was not confirmed")
    }
    @Synchronized fun command(operation: String): CommandResult = try {
        when (operation) {
            "enable" -> {
                if (!saved.exists()) {
                    val previous = state()
                    check(previous in listOf("running", "stopped")) { "Unsupported logd state: $previous" }
                    saved.writeText(previous)
                    try { change(false) } catch (e: Exception) {
                        try { change(previous == "running"); saved.delete() } catch (_: Exception) { }
                        throw e
                    }
                } else change(false)
            }
            "disable" -> if (saved.exists()) {
                val previous = saved.readText().trim()
                check(previous in listOf("running", "stopped"))
                change(previous == "running")
                check(saved.delete()) { "Cannot clear saved state" }
            }
            "status" -> Unit
            else -> error("Unsupported operation")
        }
        CommandResult(0, "LOGGING_CONTROL enabled=${saved.exists()} state=${state()}")
    } catch (e: Exception) { CommandResult(1, "Logging control failed: ${e.message}") }

    fun restoreAtBoot() { if (saved.exists()) command("enable") }
}
