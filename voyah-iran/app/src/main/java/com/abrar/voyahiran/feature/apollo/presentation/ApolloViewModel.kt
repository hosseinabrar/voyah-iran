package com.abrar.voyahiran.feature.apollo.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abrar.voyahiran.feature.apollo.data.ApolloReportReader
import com.abrar.voyahiran.feature.apollo.domain.ApolloReport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApolloUiState(val report: ApolloReport = ApolloReport(), val busy: Boolean = false)
class ApolloViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = ApolloReportReader(application)
    private val mutable = MutableStateFlow(ApolloUiState())
    val state = mutable.asStateFlow()
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
