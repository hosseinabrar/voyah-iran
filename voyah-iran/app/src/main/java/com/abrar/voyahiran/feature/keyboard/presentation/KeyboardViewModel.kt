package com.abrar.voyahiran.feature.keyboard.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.feature.keyboard.data.AndroidKeyboardPort
import com.abrar.voyahiran.feature.keyboard.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KeyboardUiState(val snapshot: KeyboardSnapshot? = null, val busy: Boolean = false, val error: KeyboardFailure? = null)
class KeyboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KeyboardRepository(AndroidKeyboardPort(application))
    private val mutable = MutableStateFlow(KeyboardUiState())
    val state = mutable.asStateFlow()
    fun refresh() = perform(null)
    fun setEnabled(enabled: Boolean) = perform(enabled)
    private fun perform(enabled: Boolean?) {
        if (mutable.value.busy) return
        com.abrar.voyahiran.core.logging.AppLog.info("Keyboard", "request enabled=$enabled")
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val actual = withContext(Dispatchers.IO) {
                    if (enabled == null) repository.read() else repository.setEnabled(enabled)
                }
                mutable.value = KeyboardUiState(actual)
                com.abrar.voyahiran.core.logging.AppLog.info("Keyboard", "current=${actual.current} gboardInstalled=${actual.gboard != null} selected=${actual.enabled}")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("Keyboard", "request-failed enabled=$enabled failure=${(error as? KeyboardException)?.failure}", error)
                val actual = withContext(Dispatchers.IO) { runCatching { repository.read() }.getOrNull() }
                mutable.value = KeyboardUiState(actual ?: mutable.value.snapshot, error = (error as? KeyboardException)?.failure ?: KeyboardFailure.FAILED)
            }
        }
    }
}
