package com.abrar.voyahiran.feature.keyboard.data

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.keyboard.domain.KeyboardException
import org.junit.Assert.*
import org.junit.Test

class KeyboardStatusReaderTest {
    @Test fun busyStartupEventuallyReadsActiveGboard() {
        var calls = 0
        val id = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        val reader = KeyboardStatusReader(CommandRunner { args, _ ->
            assertEquals(listOf("su", "0", "ime", "current", "--user", "0"), args)
            if (++calls < 4) CommandResult(75, "Bridge busy; no command executed")
            else CommandResult(0, "  mCurMethodId=$id\n")
        }, {})
        assertEquals(id, reader.read(0))
        assertEquals(4, calls)
    }
    @Test fun exhaustedBusyIsAnErrorRatherThanDisabled() {
        var calls = 0
        val reader = KeyboardStatusReader(CommandRunner { _, _ -> calls++; CommandResult(75, "busy") }, {})
        try { reader.read(0); fail("Expected failure") } catch (_: KeyboardException) {}
        assertEquals(9, calls)
    }
    @Test fun malformedAndFailedResponsesAreNotDisabled() {
        for (result in listOf(CommandResult(0, ""), CommandResult(1, "error"), CommandResult(0, "mCurMethodId=x", true))) {
            try { KeyboardStatusReader(CommandRunner { _, _ -> result }, {}).read(0); fail("Expected failure") }
            catch (_: KeyboardException) {}
        }
        assertNull(KeyboardStatusReader(CommandRunner { _, _ -> CommandResult(0, "mCurMethodId=null") }, {}).read(0))
    }
}
