package com.abrar.voyahiran.feature.rearcamera.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.feature.rearcamera.data.OemRearCameraPort
import com.abrar.voyahiran.feature.rearcamera.domain.RearCameraRepository
import com.abrar.voyahiran.feature.rearcamera.domain.RearCameraSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RearCameraUiState(val snapshot: RearCameraSnapshot? = null, val busy: Boolean = false, val error: Boolean = false)

class RearCameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RearCameraRepository(OemRearCameraPort(application))
    private val mutable = MutableStateFlow(RearCameraUiState())
    val state = mutable.asStateFlow()
    private val rebootPrompt = MutableStateFlow(false)
    val needsRebootPrompt = rebootPrompt.asStateFlow()
    fun acknowledgeRebootPrompt() { rebootPrompt.value = false }
    fun refresh() = perform(null)
    fun setEnabled(enabled: Boolean) = perform(enabled)

    private fun perform(enabled: Boolean?) {
        if (mutable.value.busy) return
        if (enabled != null) com.abrar.voyahiran.core.logging.AppLog.info("RearCamera", "request enabled=$enabled")
        mutable.value = mutable.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try {
                val actual = withContext(Dispatchers.IO) {
                    if (enabled == null) repository.read() else repository.setEnabled(enabled)
                }
                mutable.value = RearCameraUiState(actual)
                if (enabled == true) rebootPrompt.value = true
                com.abrar.voyahiran.core.logging.AppLog.changed("RearCamera", "snapshot=$actual")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("RearCamera", "request-failed enabled=$enabled", error)
                val actual = withContext(Dispatchers.IO) { runCatching { repository.read() }.getOrNull() }
                // Never leave a stale enabled checkbox after a failed or timed-out write.
                mutable.value = RearCameraUiState(actual ?: mutable.value.snapshot?.copy(status = null), error = true)
            }
        }
    }
}
