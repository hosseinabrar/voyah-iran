package com.abrar.voyahiran.feature.steering.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StarBackRepositoryTest {
    private val history = mutableListOf<String>()
    private inner class Backup : StarBackup {
        var value: Int? = null
        var failSave = false
        override var restoring = false
        override fun read() = value
        override fun save(previous: Int) {
            if (failSave) throw SteeringException(SteeringFailure.STORAGE)
            history += "save:$previous"; value = previous; restoring = false
        }
        override fun clear() { history += "clear"; value = null; restoring = false }
        override fun markRestoring() { history += "restoring"; restoring = true }
    }
    private inner class Mapping : StarMapping {
        var current = 0x41
        var failRegistration = false
        var failTarget: Int? = null
        override var listening = false
        override suspend fun read() = current
        override suspend fun write(target: Int) {
            history += "write:$target"
            if (target == failTarget) throw SteeringException(SteeringFailure.CONNECTION)
            current = target
        }
        override suspend fun intercept(previous: Int) {
            history += "intercept:$previous"
            if (failRegistration) throw SteeringException(SteeringFailure.CONNECTION)
            listening = true
        }
        override suspend fun release() { history += "release"; listening = false }
    }

    @Test fun journalAndInterceptorPrecedeOemMappingWrite() = runBlocking {
        val repo = StarBackRepository(Mapping(), Backup())
        assertTrue(repo.setEnabled(true).enabled)
        assertEquals(listOf("save:65", "intercept:65", "write:3090"), history)
    }

    @Test fun disablingRestoresExactOriginalBeforeRemovingHandlerAndJournal() = runBlocking {
        val mapping = Mapping(); val backup = Backup()
        val repo = StarBackRepository(mapping, backup)
        repo.setEnabled(true); history.clear()
        val result = repo.setEnabled(false)
        assertFalse(result.enabled)
        assertNull(result.previous)
        assertEquals(65, result.current)
        assertEquals(listOf("restoring", "write:65", "release", "clear"), history)
    }

    @Test fun repeatedEnableNeverOverwritesOriginalBackup() = runBlocking {
        val backup = Backup(); val repo = StarBackRepository(Mapping(), backup)
        repo.setEnabled(true); history.clear(); repo.setEnabled(true)
        assertTrue(history.isEmpty()); assertEquals(65, backup.value)
    }

    @Test fun storageFailureDoesNotTouchCar() = runBlocking {
        val backup = Backup().apply { failSave = true }
        assertFails(SteeringFailure.STORAGE) { StarBackRepository(Mapping(), backup).setEnabled(true) }
        assertTrue(history.isEmpty())
    }

    @Test fun registrationFailureRollsBackAndDoesNotClaimEnabled() = runBlocking {
        val mapping = Mapping().apply { failRegistration = true }; val backup = Backup()
        assertFails(SteeringFailure.CONNECTION) { StarBackRepository(mapping, backup).setEnabled(true) }
        assertEquals(65, mapping.current); assertNull(backup.value)
    }

    @Test fun failedRestoreKeepsDurableRecoveryJournal() = runBlocking {
        val mapping = Mapping(); val backup = Backup(); val repo = StarBackRepository(mapping, backup)
        repo.setEnabled(true); mapping.failTarget = 65
        assertFails(SteeringFailure.CONNECTION) { repo.setEnabled(false) }
        assertEquals(65, backup.value); assertTrue(backup.restoring)
        mapping.failTarget = null
        assertEquals(65, repo.setEnabled(false).current)
    }

    @Test fun restartReinstallsHandlerWithoutLosingBackup() = runBlocking {
        val mapping = Mapping(); val backup = Backup()
        StarBackRepository(mapping, backup).setEnabled(true)
        mapping.listening = false
        val restarted = StarBackRepository(mapping, backup)
        assertFalse(restarted.read().enabled)
        assertTrue(restarted.setEnabled(true).enabled)
        assertEquals(65, backup.value)
    }

    @Test fun explicitEnableCanSupersedePendingRestoration() = runBlocking {
        val mapping = Mapping(); val backup = Backup(); val repo = StarBackRepository(mapping, backup)
        repo.setEnabled(true); backup.markRestoring()
        assertTrue(repo.setEnabled(true).enabled)
        assertEquals(65, backup.value)
        assertEquals(65, repo.setEnabled(false).current)
    }

    @Test fun externalChangeIsVisibleAndOriginalCanStillBeRestored() = runBlocking {
        val mapping = Mapping(); val repo = StarBackRepository(mapping, Backup())
        repo.setEnabled(true); mapping.current = 99
        assertTrue(repo.read().needsRestore)
        assertEquals(65, repo.setEnabled(false).current)
    }

    @Test fun noBackupNeverInventsFactoryDefault() = runBlocking {
        assertEquals(65, StarBackRepository(Mapping(), Backup()).setEnabled(false).current)
        assertEquals(listOf("release"), history)
    }

    @Test fun rebootResetMappingIsReacquiredWithOriginalBackup() = runBlocking {
        val mapping = Mapping(); val backup = Backup(); val repo = StarBackRepository(mapping, backup)
        repo.setEnabled(true)
        mapping.current = 65; mapping.listening = false
        assertTrue(repo.setEnabled(true).enabled)
        assertEquals(65, backup.value)
    }
    @Test fun disableThenEnableCanBeRepeated() = runBlocking {
        val mapping = Mapping(); val backup = Backup(); val repo = StarBackRepository(mapping, backup)
        repeat(4) {
            assertTrue(repo.setEnabled(true).enabled)
            assertEquals(65, backup.value)
            assertEquals(65, repo.setEnabled(false).current)
            assertFalse(mapping.listening)
        }
    }
    private suspend fun assertFails(expected: SteeringFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $expected") }
        catch (error: SteeringException) { assertEquals(expected, error.failure) }
    }
}

