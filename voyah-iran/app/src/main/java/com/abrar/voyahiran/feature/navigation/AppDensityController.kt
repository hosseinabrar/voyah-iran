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

internal object AppDensityController {
    fun bind(activity: ComponentActivity, view: PageDisplayMediaMapBinding) {
        val buttons = listOf(view.appSizeTiny, view.appSizeSmall, view.appSizeNormal, view.appSizeLarge, view.appSizeHuge)
        val values = listOf("120", "140", "160", "180", "200")
        fun selected(): Int {
            val saved = Settings.Global.getString(activity.contentResolver, "voyah_iran_app_density")
            return buttons[values.indexOf(saved).takeIf { it >= 0 } ?: 2].id
        }
        view.appSizeChoices.check(selected())
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val mode = values[index]
                view.appSizeError.visibility = View.GONE
                view.appSizeProgress.visibility = View.VISIBLE
                buttons.forEach { it.isEnabled = false }
                activity.lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            ProcessCommandRunner().run(listOf("su", "0", "app-density", mode), 15)
                        }
                        view.appSizeError.visibility = if (result.exitCode == 0 && !result.timedOut) View.GONE else View.VISIBLE
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        view.appSizeError.visibility = View.VISIBLE
                    } finally {
                        view.appSizeChoices.check(selected())
                        buttons.forEach { it.isEnabled = true }
                        view.appSizeProgress.visibility = View.GONE
                    }
                }
            }
        }
    }
}
