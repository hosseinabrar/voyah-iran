package com.abrar.voyahiran.feature.launcher

/** Output pixels, identical to the test app. Change only these values after vehicle calibration. */
internal data class ClusterMapCalibration(
    val panelX: Float = 90f, val panelY: Float = 154f,
    val panelWidth: Float = 490f, val panelHeight: Float = 506f,
    val mapX: Float = 0f, val mapY: Float = 0f, val mapScale: Float = 1f,
    val maskX: Float = 0f, val maskY: Float = 0f, val maskScale: Float = 1f
) {
    val centerX get() = panelX + panelWidth / 2
    val centerY get() = panelY + panelHeight / 2
    fun mapLeft(sourceWidth: Int) = centerX + mapX - sourceWidth * mapScale / 2
    fun mapTop(sourceHeight: Int) = centerY + mapY - sourceHeight * mapScale / 2
    val maskLeft get() = centerX + maskX - panelWidth * maskScale / 2
    val maskTop get() = centerY + maskY - panelHeight * maskScale / 2
    companion object {
        // Independent presets; both deliberately start at the test app's defaults.
        val speedyDash = ClusterMapCalibration()
        val minimal = ClusterMapCalibration()
        fun current() = if (LauncherTheme.isLight()) speedyDash else minimal
    }
}
