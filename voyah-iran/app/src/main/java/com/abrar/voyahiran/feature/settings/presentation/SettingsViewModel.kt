package com.abrar.voyahiran.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.feature.settings.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val snapshot: TimeZoneSnapshot? = null,
    val busy: Boolean = false,
    val error: TimeZoneFailure? = null,
    val applied: Boolean = false,
)

class SettingsViewModel(private val repository: TimeZoneRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value.busy) return
        perform(null)
    }

    fun setCorrection(enabled: Boolean) {
        if (mutableState.value.busy || mutableState.value.snapshot == null) return
        perform(TimeZoneMode.forCorrection(enabled))
    }

    private fun perform(mode: TimeZoneMode?) {
        com.abrar.voyahiran.core.logging.AppLog.info("TimeZone", "request mode=$mode")
        mutableState.value = mutableState.value.copy(busy = true, error = null, applied = false)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    if (mode == null) repository.read() else repository.setMode(mode)
                }
                mutableState.value = SettingsUiState(snapshot, applied = mode != null)
                com.abrar.voyahiran.core.logging.AppLog.info("TimeZone", "confirmed snapshot=$snapshot applied=${mode != null}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("TimeZone", "request-failed mode=$mode failure=${(error as? TimeZoneException)?.failure}", error)
                // Re-read after failures too: a command may have partially completed before timeout.
                val actual = withContext(Dispatchers.IO) { runCatching { repository.read() }.getOrNull() }
                mutableState.value = SettingsUiState(
                    snapshot = actual,
                    error = (error as? TimeZoneException)?.failure ?: TimeZoneFailure.WRITE_FAILED,
                )
            }
        }
    }

    class Factory(private val repository: TimeZoneRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
    }
}
