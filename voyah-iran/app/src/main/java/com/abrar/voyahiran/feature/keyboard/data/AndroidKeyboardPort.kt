package com.abrar.voyahiran.feature.keyboard.data

import android.content.Context
import android.os.Process
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.keyboard.domain.*

class AndroidKeyboardPort(context: Context, private val runner: CommandRunner = ProcessCommandRunner()) : KeyboardPort {
    private val context = context.applicationContext
    private val preferences = context.getSharedPreferences("keyboard_restore", Context.MODE_PRIVATE)
    // Android allocates a range of 100000 UIDs to each user; getIdentifier is hidden on API 30.
    private val userId = Process.myUid() / 100000
    override fun read(): KeyboardSnapshot {
        val manager = context.getSystemService(InputMethodManager::class.java)
        // The passenger service shares DEFAULT_INPUT_METHOD. Ask the primary service
        // which IME is actually active rather than treating that shared key as truth.
        val status = runner.run(listOf("su", "0", "ime", "current", "--user", userId.toString()), 5)
        val current = if (status.exitCode == 0 && !status.timedOut)
            status.output.lineSequence().firstOrNull { it.trim().startsWith("mCurMethodId=") }
                ?.trim()?.removePrefix("mCurMethodId=")?.takeUnless { it == "null" }
            else null
        return KeyboardSnapshot(current,
            manager.inputMethodList.map { Keyboard(it.id, it.packageName) })
    }
    override fun previous() = preferences.getString("previous_$userId", null)
    override fun savePrevious(id: String) {
        check(preferences.edit().putString("previous_$userId", id).commit()) { "Cannot save previous keyboard" }
    }
    override fun select(id: String) {
        check(read().keyboards.any { it.id == id })
        val previousOverride = Settings.Global.getString(context.contentResolver, "voyah_iran_ime_override")
        var overrideChanged = false
        try {
            // Enable then select for this Android user; never change the separate passenger IME service.
            for (operation in listOf("enable", "set")) {
                if (operation == "set") {
                    overrideChanged = true // Also roll back if the write result is uncertain.
                    setOverride(id)
                }
                val result = runner.run(listOf("su", "0", "ime", operation, "--user", userId.toString(), id), 8)
                // OEM shell handlers can print an error yet return exit 0. This output
                // contains command diagnostics/IME IDs, never typed keyboard content.
                com.abrar.voyahiran.core.logging.AppLog.info("Keyboard", "operation=$operation target=$id exit=${result.exitCode} reply=${result.output.replace('\n', ' ').take(800)}")
                check(result.exitCode == 0 && !result.timedOut &&
                    !result.output.contains("Unknown input method", true) &&
                    !result.output.contains("restricted", true) &&
                    !result.output.contains("Exception", true)) { "Input method command failed" }
            }
            // Observe through the OEM reset window, even if the first sample matches.
            val deadline = android.os.SystemClock.elapsedRealtime() + 2000
            var current = read().current
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100)
                current = read().current
            }
            com.abrar.voyahiran.core.logging.AppLog.info("Keyboard", "verification target=$id actual=$current")
            if (current != id) throw KeyboardException(KeyboardFailure.FAILED)
        } catch (error: Exception) {
            if (overrideChanged) runCatching { setOverride(previousOverride) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun setOverride(id: String?) {
        val arguments = if (id.isNullOrBlank()) listOf("settings", "delete", "global", "voyah_iran_ime_override")
            else listOf("settings", "put", "global", "voyah_iran_ime_override", id)
        val result = runner.run(listOf("su", "0") + arguments, 8)
        check(result.exitCode == 0 && !result.timedOut && !result.output.contains("Exception", true)) { "IME override failed" }
    }
}
