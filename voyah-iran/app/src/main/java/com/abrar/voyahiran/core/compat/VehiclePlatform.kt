package com.abrar.voyahiran.core.compat

/** Framework/launcher hooks are verified for Android 11. Vehicle options remain SDK-driven. */
object VehiclePlatform {
    val isH97a: Boolean get() = listOf(android.os.Build.DEVICE, android.os.Build.PRODUCT,
        android.os.Build.MODEL, android.os.Build.BOARD, android.os.Build.FINGERPRINT)
        .any { it.contains("h97a", ignoreCase = true) }
    val launcherDrawer: Boolean by lazy {
        com.abrar.voyahiran.feature.launcher.LauncherFallbackPolicy.requiresDrawer(
            android.os.Build.DEVICE, android.os.Build.PRODUCT, android.os.Build.MODEL,
            android.os.Build.BOARD, android.os.Build.FINGERPRINT)
    }
    val localProfileTest: Boolean get() = listOf(android.os.Build.DEVICE, android.os.Build.PRODUCT,
        android.os.Build.MODEL, android.os.Build.BOARD, android.os.Build.FINGERPRINT)
        .any { Regex("(?i)(?<![a-z0-9])h97[ac](?![a-z0-9])").containsMatchIn(it) }
    val screenSwap: Boolean get() = com.abrar.voyahiran.core.bridge.ScreenSwapContract.forSdk(android.os.Build.VERSION.SDK_INT) != null
    val frameworkHooks: Boolean get() = android.os.Build.VERSION.SDK_INT == 30
    // Separate from OEM launcher/screen-swap hooks, which still use Android 11 contracts.
    val displayFrameworkHooks: Boolean get() = android.os.Build.VERSION.SDK_INT in setOf(28, 30)
    val frameworkUnavailable: String get() = "این قابلیت نمایشگر هنوز برای اندروید ${android.os.Build.VERSION.RELEASE} آماده نشده است."
}
