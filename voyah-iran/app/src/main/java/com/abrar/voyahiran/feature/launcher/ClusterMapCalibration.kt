package com.abrar.voyahiran.feature.launcher

/** Output pixels, identical to the test app. Change only these values after vehicle calibration. */
internal data class ClusterMapCalibration(
    val panelX: Float = 90f, val panelY: Float = 154f,
    val panelWidth: Float = 490f, val panelHeight: Float = 506f,
    val mapX: Float = 0f, val mapY: Float = 0f, val mapScale: Float = 1f,
    val maskX: Float = 0f, val maskY: Float = 0f, val maskScale: Float = 1f,
    val maskEnabled: Boolean = true
) {
    val centerX get() = panelX + panelWidth / 2
    val centerY get() = panelY + panelHeight / 2
    fun mapLeft(sourceWidth: Int) = centerX + mapX - sourceWidth * mapScale / 2
    fun mapTop(sourceHeight: Int) = centerY + mapY - sourceHeight * mapScale / 2
    val maskLeft get() = centerX + maskX - panelWidth * maskScale / 2
    val maskTop get() = centerY + maskY - panelHeight * maskScale / 2
    companion object {
        @Volatile var themeId = 4
        val speedyDash = ClusterMapCalibration(mapX=-20f,mapY=-21f,maskX=-24f,maskScale=.99f)
        val minimal = ClusterMapCalibration(mapX=-17f,mapY=-6f,maskEnabled=false)
        val classic = ClusterMapCalibration(mapX=28f,mapY=-19f,mapScale=1.02f,maskEnabled=false)
        fun select(theme:Int,routing:Boolean) = when(theme) {
            1 -> if(routing) minimal.copy(mapX=-159f,mapY=-29f,mapScale=1.14f) else minimal
            2 -> if(routing) classic.copy(mapX=-194f,mapY=-19f,mapScale=1.04f) else classic
            else -> if(routing) speedyDash.copy(mapX=-229f,mapY=-31f) else speedyDash
        }
        fun current() = select(themeId,com.abrar.voyahiran.feature.navigation.NeshanDrivingService.clusterRouting)
    }
}
