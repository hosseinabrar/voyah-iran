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
import com.abrar.voyahiran.core.bridge.AppDensityPreferences
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import android.provider.Settings

data class PermissionsState(val apps: List<InstalledApp> = emptyList(), val selected: String? = null,
    val permissions: List<AppPermission> = emptyList(), val busy: Boolean = false, val error: Boolean = false, val stoppedApp: String? = null,
    val defaultDensity: Int = 160, val customDensity: Int? = null)
class PermissionsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PermissionRepository(app)
    private val mutable = MutableStateFlow(PermissionsState())
    val state = mutable.asStateFlow()
    fun refresh() = runOperation {
        val apps = repository.apps()
        val selected = mutable.value.selected?.takeIf { p -> apps.any { it.packageName == p } } ?: apps.firstOrNull()?.packageName
        mutable.value.copy(apps = apps, selected = selected, permissions = selected?.let(repository::permissions).orEmpty())
    }
    fun uninstall(pkg: String) = runOperation {
        repository.uninstall(pkg)
        val apps = repository.apps()
        val selected = apps.firstOrNull()?.packageName
        mutable.value.copy(apps = apps, selected = selected,
            permissions = selected?.let(repository::permissions).orEmpty())
    }
    fun forceStop() {
        val pkg = mutable.value.selected ?: return
        runOperation {
            repository.forceStop(pkg)
            mutable.value.copy(stoppedApp = mutable.value.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg)
        }
    }
    fun setDensity(mode: String) {
        val pkg = mutable.value.selected ?: return
        runOperation {
            val result = ProcessCommandRunner().run(listOf("su", "0", "app-density-package", pkg, mode), 15)
            check(result.exitCode == 0 && !result.timedOut) { "App density update not acknowledged" }
            mutable.value
        }
    }
    private fun withDensity(state: PermissionsState): PermissionsState {
        val resolver = getApplication<Application>().contentResolver
        val global = Settings.Global.getString(resolver, AppDensityPreferences.GLOBAL)?.toIntOrNull()
            ?.takeIf { it in AppDensityPreferences.values } ?: 160
        val custom = AppDensityPreferences.parse(Settings.Global.getString(resolver, AppDensityPreferences.PACKAGES))[state.selected]
        return state.copy(defaultDensity = global, customDensity = custom)
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
        mutable.value = mutable.value.copy(busy = true, error = false, stoppedApp = null)
        viewModelScope.launch {
            try { mutable.value = withContext(Dispatchers.IO) { withDensity(block()) }.copy(busy = false, error = false) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("Permissions", "operation failed package=${mutable.value.selected}", e)
                                val current = mutable.value.selected?.let { pkg ->
                    try { withContext(Dispatchers.IO) { repository.permissions(pkg) } } catch (_: Exception) { null }
                }
                mutable.value = withDensity(mutable.value.copy(permissions = current ?: mutable.value.permissions, busy = false, error = true))
            }
        }
    }
}

