package com.abrar.voyahiran.feature.launcher

object LauncherFallbackPolicy {
    const val SETTING = "voyah_iran_launcher_fallback"
    const val PACKAGE = "com.qinggan.app.launcher"
    fun requiresDrawer(vararg identifiers: String): Boolean = identifiers.any {
        Regex("(?i)(?<![a-z0-9])h97[ac](?![a-z0-9])").containsMatchIn(it)
    }
    fun targetDisplay(display: Int?, h97a: Boolean): Int? = when (display) {
        0 -> 0
        1 -> if (h97a) null else 1
        else -> null
    }
    // Called only after accepting screen_up_all_app_icon. Some H97C firmware
    // has no getDisplayId on AccessibilityEvent (nor on its window).
    fun buttonDisplay(eventDisplay: Int?, windowDisplay: Int?): Int =
        eventDisplay?.takeIf { it >= 0 } ?: windowDisplay?.takeIf { it >= 0 } ?: 0
    fun accepts(enabled: Boolean, packageName: String?, viewId: String?): Boolean =
        enabled && packageName == PACKAGE && viewId == "$PACKAGE:id/screen_up_all_app_icon"
}
