package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject

data class LauncherVehicleState(
    val drive: Int? = null, val height: Int? = null, val adjusting: Int? = null,
    val recovery: Int? = null, val energy: Int? = null,
    val modes: Map<String, Int> = emptyMap(), val speed: Int? = null,
    val pressure: Int? = null, val maintain: Int? = null, val suspended: Int? = null,
    val sensorError: Int? = null, val wheels: List<Int?> = List(4) { null },
    val manual: Int? = null, val fresh: Boolean = false,
    val supportsSuspension: Boolean = false, val supportsOuting: Boolean = false
) {
    val suspensionEnabled get() = fresh && supportsSuspension && height in 1..9 && maintain in 0..1 && suspended in listOf(0,2)
    val heightIndex get() = when(height) { 9 -> 0; 7,8 -> 1; 3,4,5,6 -> 2; 1,2 -> 3; else -> -1 }
    val heightLabel get() = if(height in listOf(2,4,6,8) && adjusting in 1..2)
        if(adjusting == 1) "Rising…" else "Lowering…"
        else when(height) { 1,2 -> "Highest"; 3 -> "High"; 4,5,6 -> "Medium"; 7,8 -> "Low"; 9 -> "Lowest"; else -> "—" }
    // CCC body_20/40/60/80 are Lowest/Low/Medium/Highest. Frame time is 10 ms.
    val bodyFrame get() = when(height) {9 -> 20;7,8 -> 40;3,4,5,6 -> 60;1,2 -> 80;else -> null}
    val groundMm get() = when(height) {9 -> 113;7 -> 133;5 -> 163;1 -> 213;else -> null}
    companion object {
        val driveNames = linkedMapOf(1 to "Eco", 2 to "Comfort", 3 to "Sport", 4 to "Outing", 6 to "Snow", 5 to "Individual")
        val energyNames = mapOf("smart" to "Smart", "electric" to "EV first", "fuel" to "Fuel first", "hybrid" to "Hybrid", "save" to "Save")
        fun parse(json: JSONObject): LauncherVehicleState {
            fun n(key: String) = if(json.isNull(key)) null else json.optInt(key, -1).takeUnless { it == -1 }
            val m=json.optJSONObject("modes")
            return LauncherVehicleState(n("DRIVING_MODE_SET"),n("ASC_MODE_SELECT"),n("ASC_ADJUST_DIREACT"),
                n("HUM_ENERGY_PTREGEN_LEVL_FB"),n("IVI_SOC_MODESET"),
                m?.keys()?.asSequence()?.associateWith { m.getInt(it) }?.toSortedMap(compareBy { listOf("smart","electric","hybrid","fuel","save").indexOf(it) }) ?: emptyMap(),
                n("speed"),n("AMBIENT_AIR_PRESSURE"),n("ASC_MAINTAIN_SWITCH"),n("ASC_ADJUST_SUSTEMP"),n("ASC_SENSOR_ERROR_STS"),
                listOf("ASC_RF_HEIGHT","ASC_LF_HEIGHT","ASC_LR_HEIGHT","ASC_RR_HEIGHT").map(::n),n("MANUAL_EASY_ENTER_SET"),true,
                json.optBoolean("supportsSuspension",false),json.optBoolean("supportsOuting",false))
        }
        fun swipePage(page: Int, dx: Float, dy: Float, width: Int): Int =
            if(kotlin.math.abs(dx) > width * .08f && kotlin.math.abs(dx) > kotlin.math.abs(dy)*1.5f)
                (page + if(dx<0) 1 else -1).coerceIn(0,1) else page
    }
}
