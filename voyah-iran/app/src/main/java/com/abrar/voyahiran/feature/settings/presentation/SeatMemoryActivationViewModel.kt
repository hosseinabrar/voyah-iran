package com.abrar.voyahiran.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SeatMemoryActivationViewModel : ViewModel() {
    private val mutable = MutableStateFlow(LocalProfileUiState())
    val state = mutable.asStateFlow()
    fun run(operation: String) {
        if (mutable.value.busy || operation !in listOf("enable", "disable", "status")) return
        val previous = mutable.value
        mutable.value = previous.copy(busy = true, text = "")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    var value = ProcessCommandRunner().run(listOf("su", "0", "seat-memory-activation", operation), 5)
                    repeat(2) {
                        if (value.exitCode == 75) {
                            delay(500)
                            value = ProcessCommandRunner().run(listOf("su", "0", "seat-memory-activation", operation), 5)
                        }
                    }
                    value
                }
                val desired = when {
                    result.output.contains("seat_memory_desired=true") -> true
                    result.output.contains("seat_memory_desired=false") -> false
                    else -> null
                }
                check(result.exitCode == 0 && !result.timedOut && desired != null)
                check(operation == "status" || desired == (operation == "enable"))
                mutable.value = LocalProfileUiState(enabled = desired)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLog.error("SeatMemoryActivation", "Setting failed: $operation", e)
                mutable.value = previous.copy(busy = false, text = "تغییر تأیید نشد؛ دوباره تلاش کنید.")
            }
        }
    }
}
