package com.abrar.voyahiran.feature.steering.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Qinggan key codes, not android.view.KeyEvent key codes. */
object SteeringKeys {
    const val STAR = 0xC12
    const val BACK = 0x21
}

interface StarMapping {
    suspend fun read(): Int
    suspend fun write(target: Int)
    suspend fun intercept(previous: Int)
    suspend fun release()
    val listening: Boolean
}

/** Durable recovery journal: save must complete before the first OEM write. */
interface StarBackup {
    fun read(): Int?
    fun save(previous: Int)
    fun clear()
    fun markRestoring()
    val restoring: Boolean
}

enum class SteeringFailure { UNAVAILABLE, CONNECTION, STORAGE, VERIFICATION, NO_BACKUP }
class SteeringException(val failure: SteeringFailure, cause: Throwable? = null) : Exception(failure.name, cause)

data class StarBackSnapshot(val current: Int, val previous: Int?, val listening: Boolean, val restoring: Boolean = false) {
    val enabled: Boolean get() = current == SteeringKeys.STAR && previous != null && listening && !restoring
    val needsRestore: Boolean get() = previous != null && !enabled
}

class StarBackRepository(private val mapping: StarMapping, private val backup: StarBackup) {
    private val mutex = Mutex()

    suspend fun read(): StarBackSnapshot = mutex.withLock { snapshot() }

    suspend fun setEnabled(enabled: Boolean): StarBackSnapshot = mutex.withLock {
        // Losing the Activity must not interrupt a write between saving and restoring its journal.
        withContext(NonCancellable) {
            val before = snapshot()
            if (enabled) {
                if (before.restoring && before.previous != null) backup.save(before.previous)
                if (before.enabled) return@withContext before
                val previous = before.previous ?: before.current
                if (before.previous == null) backup.save(previous)
                try {
                    mapping.intercept(previous)
                    setAndVerify(SteeringKeys.STAR)
                } catch (error: Exception) {
                    // The OEM queues writes asynchronously. Restore through the same queue.
                    runCatching { backup.markRestoring(); setAndVerify(previous); mapping.release(); backup.clear() }
                    throw error
                }
            } else {
                val previous = before.previous ?: run { mapping.release(); return@withContext snapshot() }
                backup.markRestoring()
                setAndVerify(previous)
                mapping.release()
                backup.clear() // Never erase the recovery value before verified restoration.
            }
            snapshot()
        }
    }

    private suspend fun snapshot() = StarBackSnapshot(mapping.read(), backup.read(), mapping.listening, backup.restoring)

    private suspend fun setAndVerify(target: Int) {
        mapping.write(target)
        // The Binder reply only acknowledges enqueueing, not that the mapping was applied.
        repeat(20) {
            if (mapping.read() == target) return
            delay(100)
        }
        throw SteeringException(SteeringFailure.VERIFICATION)
    }
}

