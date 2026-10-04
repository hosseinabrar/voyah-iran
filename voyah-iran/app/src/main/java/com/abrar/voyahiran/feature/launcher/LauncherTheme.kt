package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings

internal object LauncherTheme {
    private val propertyReader by lazy { runCatching { Class.forName("android.os.SystemProperties").getMethod("get",String::class.java,String::class.java) }.getOrNull() }
    fun isLight(context: Context): Boolean {
        val mode=Settings.Global.getString(context.contentResolver,"voyah_iran_app_theme")
        val vehicle=runCatching { propertyReader?.invoke(null,"persist.sys.qg.currenttheme","") as? String }.getOrNull()
        return resolve(mode,vehicle,context.resources.configuration.uiMode)
    }
    internal fun resolve(mode: String?, vehicle: String?, uiMode: Int): Boolean = when(mode) {
        "light" -> true
        "dark" -> false
        else -> if(!vehicle.isNullOrEmpty()) vehicle=="simple" else uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_NO
    }
}
