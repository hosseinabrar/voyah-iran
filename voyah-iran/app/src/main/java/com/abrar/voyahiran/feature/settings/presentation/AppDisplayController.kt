package com.abrar.voyahiran.feature.settings.presentation

import android.content.Context
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.settings.data.AppDisplayPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppDisplayState(val enabled: Boolean = true, val busy: Boolean = false, val failed: Boolean = false)

class AppDisplayController(context: Context) {
    private val preferences = AppDisplayPreferences(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(AppDisplayState(preferences.enabled))
    val state = mutable.asStateFlow()

    fun refresh() = setEnabled(preferences.enabled)
    fun setEnabled(enabled: Boolean) {
        if (mutable.value.busy) return
        val previous = preferences.enabled
        mutable.value = AppDisplayState(previous, busy = true)
        scope.launch {
            val success = try {
                val result = withContext(Dispatchers.IO) {
                    ProcessCommandRunner().run(listOf("su", "0", "app-display", if (enabled) "enable" else "disable"), 25)
                }
                AppLog.info("AppDisplay", "requested=$enabled exit=" + result.exitCode + " result=" + result.output)
                result.exitCode == 0 && !result.timedOut && result.output.contains("DISPLAY_POLICY_APPLIED")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.error("AppDisplay", "update failed", e)
                false
            }
            if (success) preferences.enabled = enabled
            mutable.value = AppDisplayState(if (success) enabled else previous, failed = !success)
        }
    }
}
