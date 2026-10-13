package com.abrar.voyahiran.feature.apollo.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.feature.apollo.data.ApolloReportReader
import com.abrar.voyahiran.feature.apollo.domain.ApolloReport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApolloUiState(val report: ApolloReport = ApolloReport(), val busy: Boolean = false, val error: String? = null)
class ApolloViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = ApolloReportReader(application)
    private val mutable = MutableStateFlow(ApolloUiState())
    val state = mutable.asStateFlow()
    fun setFeature(feature: com.abrar.voyahiran.feature.apollo.domain.ApolloFeature, enabled: Boolean) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            var failure: String? = null
            try {
                withContext(Dispatchers.IO) {
                    var result = com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                        listOf("su", "0", "apollo-feature", feature.name, if (enabled) "on" else "off"), 5)
                    // Only retry a request rejected before execution. Never replay an uncertain write.
                    var attempts = 0
                    while (result.exitCode == 75 && !result.timedOut && attempts++ < 4) {
                        delay(350)
                        result = com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                            listOf("su", "0", "apollo-feature", feature.name, if (enabled) "on" else "off"), 5)
                    }
                    check(result.exitCode == 0 && !result.timedOut) { result.output }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("Apollo", "Feature change failed: $feature", e)
                failure = "تغییر تنظیم در خودرو تأیید نشد."
            }
            val report = try { withContext(Dispatchers.IO) { reader.read() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failure = "خواندن وضعیت خودرو ناموفق بود."
                ApolloReport()
            }
            mutable.value = ApolloUiState(report, error = failure)
        }
    }
    fun setExperiment(setting: com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment, value: Int) {
        if (mutable.value.busy || !com.abrar.voyahiran.feature.apollo.data.ApolloControl.checked(getApplication())) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            var failure: String? = null
            try {
                withContext(Dispatchers.IO) { com.abrar.voyahiran.feature.apollo.data.ApolloExperimentPort.set(setting, value) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("ApolloTest", "Write failed: $setting=$value", e)
                failure = "تغییر تنظیم در خودرو تأیید نشد."
            }
            val report = try { withContext(Dispatchers.IO) { reader.read() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { ApolloReport() }
            mutable.value = ApolloUiState(report, error = failure)
        }
    }
    fun setEnabled(enabled: Boolean) {
        if (mutable.value.busy) return
        val app = getApplication<Application>()
        com.abrar.voyahiran.feature.apollo.data.ApolloControl.save(app, enabled)
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { com.abrar.voyahiran.feature.apollo.data.ApolloControl.sync(app, force = true) }
                mutable.value = ApolloUiState(withContext(Dispatchers.IO) { reader.read() })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("Apollo", "Apply failed", e)
                mutable.value = mutable.value.copy(busy = false, error = "تنظیم ذخیره شد؛ ارسال به خودرو ناموفق بود.")
            }
        }
    }
    fun refresh() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try { mutable.value = ApolloUiState(withContext(Dispatchers.IO) { reader.read() }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = ApolloUiState() }
        }
    }
}
