package com.abrar.voyahiran.feature.keyboard

import com.abrar.voyahiran.feature.keyboard.domain.*
import org.junit.Assert.*
import org.junit.Test

class KeyboardRepositoryTest {
    private val original = Keyboard("com.qinggan.app.qgime/.QingganIME", "com.qinggan.app.qgime")
    private val google = Keyboard("${KeyboardSnapshot.GBOARD}/.Ime", KeyboardSnapshot.GBOARD)
    private inner class Port : KeyboardPort {
        var snapshot = KeyboardSnapshot(original.id, listOf(original, google))
        var saved: String? = null
        var selections = 0
        var saveFails = false
        var confirms = true
        override fun read() = snapshot
        override fun previous() = saved
        override fun savePrevious(id: String) { check(!saveFails); saved = id }
        override fun select(id: String) { selections++; if (confirms) snapshot = snapshot.copy(current = id) }
    }
    @Test fun enableAndRestore() {
        val p = Port(); val r = KeyboardRepository(p)
        assertTrue(r.setEnabled(true).enabled); assertEquals(original.id, p.saved)
        assertFalse(r.setEnabled(false).enabled); assertEquals(original.id, p.snapshot.current)
    }
    @Test fun missingGoogleDoesNotWrite() {
        val p = Port().apply { snapshot = snapshot.copy(keyboards = listOf(original)) }
        val e = runCatching { KeyboardRepository(p).setEnabled(true) }.exceptionOrNull() as KeyboardException
        assertEquals(KeyboardFailure.NOT_INSTALLED, e.failure); assertEquals(0, p.selections)
    }
    @Test fun alreadySelectedDoesNotOverwriteBackup() {
        val p = Port().apply { snapshot = snapshot.copy(current = google.id); saved = original.id }
        KeyboardRepository(p).setEnabled(true)
        assertEquals(original.id, p.saved); assertEquals(0, p.selections)
    }
    @Test fun backupFailurePreventsMutation() {
        val p = Port().apply { saveFails = true }
        assertTrue(runCatching { KeyboardRepository(p).setEnabled(true) }.isFailure)
        assertEquals(0, p.selections)
    }
    @Test fun externallySelectedGboardCanRestoreOem() {
        val p = Port().apply { snapshot = snapshot.copy(current = google.id) }
        assertEquals(original.id, KeyboardRepository(p).setEnabled(false).current)
    }
    @Test fun passengerIsNotChosenAsFallback() {
        val p = Port().apply { snapshot = KeyboardSnapshot(google.id, listOf(google,
            Keyboard("com.qinggan.app.qgime.second/com.qinggan.app.qgime.QingganIME", "com.qinggan.app.qgime.second"))) }
        assertTrue(runCatching { KeyboardRepository(p).setEnabled(false) }.isFailure)
        assertEquals(0, p.selections)
    }
    @Test fun unconfirmedSelectionFails() {
        val p = Port().apply { confirms = false }
        assertTrue(runCatching { KeyboardRepository(p).setEnabled(true) }.isFailure)
        assertFalse(p.read().enabled)
    }
    @Test fun restoredKeyboardMustStillExist() {
        val p = Port().apply { saved = "missing/.Ime"; snapshot = KeyboardSnapshot(google.id, listOf(google)) }
        assertTrue(runCatching { KeyboardRepository(p).setEnabled(false) }.isFailure)
        assertEquals(0, p.selections)
    }
}
