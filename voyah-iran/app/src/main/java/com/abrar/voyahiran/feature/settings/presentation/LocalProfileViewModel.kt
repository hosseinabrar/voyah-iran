package com.abrar.voyahiran.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalProfileUiState(val busy: Boolean = false, val text: String = "", val enabled: Boolean? = null)
class LocalProfileViewModel : ViewModel() {
    private val mutable = MutableStateFlow(LocalProfileUiState())
    val state = mutable.asStateFlow()
    fun run(operation: String) {
        if (mutable.value.busy || operation !in listOf("status", "apply", "restore")) return
        mutable.value = mutable.value.copy(busy=true, text="")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    var r = ProcessCommandRunner().run(listOf("su", "0", "local-profile-test", operation), 15)
                    repeat(2) {
                        // Busy means no command executed; retry only this unambiguous case.
                        if (r.exitCode == 75) {
                            delay(500)
                            r = ProcessCommandRunner().run(listOf("su", "0", "local-profile-test", operation), 15)
                        }
                    }
                    r
                }
                AppLog.info("LocalProfile", "operation=$operation exit=${result.exitCode} timedOut=${result.timedOut} output=${result.output.take(4000)}")
                val enabled = when {
                    result.output.contains("login_activity_blocked=true") -> true
                    result.output.contains("login_activity_blocked=false") -> false
                    else -> null
                }
                check(result.exitCode == 0 && !result.timedOut && enabled != null) { "Unconfirmed state" }
                check(operation != "apply" || enabled) { "Enable not confirmed" }
                check(operation != "restore" || !enabled) { "Disable not confirmed" }
                mutable.value = LocalProfileUiState(enabled=enabled)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLog.error("LocalProfile", "Unable to confirm activation setting", e)
                mutable.value = LocalProfileUiState(text="تغییر تأیید نشد؛ برای بررسی مجدد روی گزینه بزنید.")
            }
        }
    }
}
