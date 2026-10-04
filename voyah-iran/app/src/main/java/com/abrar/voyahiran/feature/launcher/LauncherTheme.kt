package com.abrar.voyahiran.feature.launcher

/** Launcher follows the vehicle theme, independently of third-party app theme overrides. */
internal object LauncherTheme {
    private val propertyReader by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }
    fun isLight(): Boolean {
        val vehicle = runCatching {
            propertyReader?.invoke(null, "persist.sys.qg.currenttheme", "") as? String
        }.getOrNull()
        return resolve(vehicle)
    }
    // Speedy Dash is named "simple" in the OEM property. Unknown themes default to dark.
    internal fun resolve(vehicle: String?): Boolean = vehicle == "simple"
}
