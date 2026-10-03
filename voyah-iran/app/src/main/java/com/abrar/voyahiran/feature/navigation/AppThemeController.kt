package com.abrar.voyahiran.feature.navigation

import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.R
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.databinding.PageDisplayMediaMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object AppThemeController {
    fun bind(activity: ComponentActivity, view: PageDisplayMediaMapBinding) {
        fun selected() = when (Settings.Global.getString(activity.contentResolver, "voyah_iran_app_theme")) {
            "dark" -> R.id.appThemeDark
            "light" -> R.id.appThemeLight
            else -> R.id.appThemeVehicle
        }
        view.appThemeChoices.check(selected())
        listOf(view.appThemeDark, view.appThemeLight, view.appThemeVehicle).forEach { button ->
            button.setOnClickListener {
                val mode = when (button.id) { R.id.appThemeDark -> "dark"; R.id.appThemeLight -> "light"; else -> "vehicle" }
                view.appThemeError.visibility = View.GONE
                view.appThemeProgress.visibility = View.VISIBLE
                val buttons = listOf(view.appThemeDark, view.appThemeLight, view.appThemeVehicle)
                buttons.forEach { it.isEnabled = false }
                activity.lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "app-theme", mode), 15)
                        }
                        view.appThemeError.visibility = if (result.exitCode == 0 && !result.timedOut) View.GONE else View.VISIBLE
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        view.appThemeError.visibility = View.VISIBLE
                    } finally {
                        view.appThemeChoices.check(selected())
                        buttons.forEach { it.isEnabled = true }
                        view.appThemeProgress.visibility = View.GONE
                    }
                }
            }
        }
    }
}
