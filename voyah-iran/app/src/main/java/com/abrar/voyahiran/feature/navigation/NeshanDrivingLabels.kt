package com.abrar.voyahiran.feature.navigation

/** Shared text normalization; actual UI labels are supplied by the signed profile. */
internal object NeshanDrivingLabels {
    fun normalize(text: String): String = ClusterNavigationText.clean(text
        .replace('\u064a', '\u06cc').replace('\u0643', '\u06a9'))
}
