package com.abrar.voyahiran.feature.settings.data

import android.content.Context

/** Desired policy; persisted after the framework acknowledges the change. */
class AppDisplayPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_display", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", true)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
}
