package com.abrar.voyahiran.feature.steering.domain

import com.abrar.voyahiran.feature.steering.data.PreferencesStarBackup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StarDesiredStateTest {
    private fun preferences() = RuntimeEnvironment.getApplication().getSharedPreferences("star-test-${java.util.UUID.randomUUID()}", 0)
    @Test fun desiredSurvivesJournalClearAndNewInstance() {
        val prefs = preferences()
        val state = PreferencesStarBackup(prefs)
        state.desired = true
        state.save(65); state.markRestoring(); state.clear()
        assertTrue(PreferencesStarBackup(prefs).desired)
        assertNull(state.read())
    }
    @Test fun offPersistsWhileRestorationIsPending() {
        val prefs = preferences(); val state = PreferencesStarBackup(prefs)
        state.desired = true; state.save(65)
        state.desired = false; state.markRestoring()
        assertFalse(PreferencesStarBackup(prefs).desired)
        assertEquals(65, state.read())
    }
    @Test fun legacyEnabledStateCanBeMigratedBeforeRollback() {
        val state = PreferencesStarBackup(preferences())
        state.save(65)
        state.desired = state.desired
        state.clear()
        assertTrue(state.desired)
    }
}
