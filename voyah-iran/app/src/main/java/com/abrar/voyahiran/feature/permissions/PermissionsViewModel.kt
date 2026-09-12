package com.abrar.voyahiran.feature.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.abrar.voyahiran.core.logging.AppLog

data class PermissionsState(val apps: List<InstalledApp> = emptyList(), val selected: String? = null,
    val permissions: List<AppPermission> = emptyList(), val busy: Boolean = false, val error: Boolean = false)
class PermissionsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PermissionRepository(app)
    private val mutable = MutableStateFlow(PermissionsState())
    val state = mutable.asStateFlow()
    fun refresh() = runOperation {
        val apps = repository.apps()
        val selected = mutable.value.selected?.takeIf { p -> apps.any { it.packageName == p } } ?: apps.firstOrNull()?.packageName
        mutable.value.copy(apps = apps, selected = selected, permissions = selected?.let(repository::permissions).orEmpty())
    }
    fun select(pkg: String) {
        if (mutable.value.selected == pkg || mutable.value.busy) return
        mutable.value = mutable.value.copy(selected = pkg, permissions = emptyList())
        runOperation { mutable.value.copy(permissions = repository.permissions(pkg)) }
    }
    fun set(permission: AppPermission, enabled: Boolean) {
        val pkg = mutable.value.selected ?: return
        runOperation {
            repository.set(pkg, permission.name, enabled)
            mutable.value.copy(permissions = repository.permissions(pkg))
        }
    }
    private fun runOperation(block: () -> PermissionsState) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try { mutable.value = withContext(Dispatchers.IO) { block() }.copy(busy = false, error = false) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Permissions", "operation failed package=${mutable.value.selected}", e)
                                val current = mutable.value.selected?.let { pkg ->
                    try { withContext(Dispatchers.IO) { repository.permissions(pkg) } } catch (_: Exception) { null }
                }
                mutable.value = mutable.value.copy(permissions = current ?: mutable.value.permissions, busy = false, error = true)
            }
        }
    }
}

