package com.abrar.voyahiran.feature.launcher

/** OEM identifiers, never translated labels or user-installed package names. */
internal object LauncherAppsPolicy {
    const val COLUMNS = 9
    const val ROWS = 3
    const val PAGE_SIZE = COLUMNS * ROWS
    val hidden = setOf("Navigation", "HiCar", "UserCenter", "WEATHER", "Karaoke",
        "VoiceGame", "FreeBaike", "Bill", "ServiceFolder", "LifeServiceFolder",
        "QuickAppFolder", "QuickAppItem", "QuickApp", "CloudApp", "WecarFlow")
    fun visible(subtype: String) = subtype.isNotBlank() && subtype !in hidden
    fun pages(count: Int) = ((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
}
