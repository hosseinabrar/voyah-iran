package com.abrar.voyahiran.feature.keyboard.data

import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.keyboard.domain.KeyboardException
import com.abrar.voyahiran.feature.keyboard.domain.KeyboardFailure

/** Retry only read-only requests explicitly rejected as busy; never repeat a write. */
internal class KeyboardStatusReader(
    private val runner: CommandRunner,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun read(userId: Int): String? {
        repeat(9) { attempt ->
            val status = runner.run(listOf("su", "0", "ime", "current", "--user", userId.toString()), 5)
            if (status.exitCode == 75 && !status.timedOut && attempt < 8) {
                pause(250L + attempt * 100L)
            } else {
                if (status.exitCode != 0 || status.timedOut) throw KeyboardException(KeyboardFailure.FAILED)
                val value = status.output.lineSequence().map { it.trim() }
                    .firstOrNull { it.startsWith("mCurMethodId=") }
                    ?.removePrefix("mCurMethodId=")?.trim()
                    ?: throw KeyboardException(KeyboardFailure.FAILED)
                return value.takeUnless { it == "null" || it.isEmpty() }
            }
        }
        throw KeyboardException(KeyboardFailure.FAILED)
    }
}
