package com.abrar.voyahiran.feature.steering.presentation

import com.abrar.voyahiran.feature.steering.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SteeringUiState(val snapshot: StarBackSnapshot? = null, val busy: Boolean = false, val error: SteeringFailure? = null)

/** Shared by the foreground service and UI, so rotating the Activity never owns the handler. */
class SteeringController(private val repository: StarBackRepository, private val mayEnable: () -> Boolean = { true }) {
    private val mutableState = MutableStateFlow(SteeringUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()

    suspend fun refresh() = perform(null)
    suspend fun setEnabled(enabled: Boolean) = perform(enabled)

    private suspend fun perform(enabled: Boolean?): Boolean = mutex.withLock {
        if (enabled != null) com.abrar.voyahiran.core.logging.AppLog.info("Steering", "request enabled=$enabled")
        withContext(Dispatchers.IO) {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            try {
                val actual = if (enabled == null) repository.read() else repository.setEnabled(enabled && mayEnable())
                mutableState.value = SteeringUiState(actual)
                com.abrar.voyahiran.core.logging.AppLog.changed("Steering", "snapshot=$actual")
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("Steering", "request-failed enabled=$enabled", error)
                val actual = runCatching { repository.read() }.getOrNull()
                mutableState.value = SteeringUiState(actual, error =
                    (error as? SteeringException)?.failure ?: SteeringFailure.CONNECTION)
                false
            }
        }
    }
}
