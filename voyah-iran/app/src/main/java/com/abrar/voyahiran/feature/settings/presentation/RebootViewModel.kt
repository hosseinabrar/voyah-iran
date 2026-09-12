package com.abrar.voyahiran.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

enum class RebootState { IDLE, REQUESTING, REQUESTED, UNCONFIRMED }

class RebootViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(RebootState.IDLE)
    val state = mutableState.asStateFlow()

    fun reboot() {
        if (mutableState.value == RebootState.REQUESTING || mutableState.value == RebootState.REQUESTED) return
        mutableState.value = RebootState.REQUESTING
        viewModelScope.launch {
            try {
                AppLog.info("Reboot", "User confirmed device reboot; sending once through system bridge")
                val result = withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su", "0", "reboot"), 15)
                }
                AppLog.info("Reboot", "request exit=${result.exitCode} timeout=${result.timedOut} output=${result.output}")
                mutableState.value = if (result.exitCode == 0 && !result.timedOut) RebootState.REQUESTED else RebootState.UNCONFIRMED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.info("Reboot", "Request unconfirmed; not retried: $error")
                mutableState.value = RebootState.UNCONFIRMED
            }
        }
    }
}
