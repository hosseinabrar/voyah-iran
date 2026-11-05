package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject

/** OEM values are already decoded: percent, rpm, km, minutes, L/100km and kWh/100km. */
internal data class LauncherTelemetry(
    val battery:Double?=null, val fuel:Double?=null, val rpm:Double?=null,
    val instantFuel:Double?=null, val distance:Double?=null, val minutes:Double?=null,
    val averageFuel:Double?=null, val averagePower:Double?=null,
    val batteryRange:Double?=null, val fuelRange:Double?=null
) {
    companion object {
        fun parse(j:JSONObject):LauncherTelemetry {
            fun value(key:String,max:Double,min:Double=0.0):Double? = if(j.isNull(key)) null else
                j.optDouble(key,Double.NaN).takeIf { it.isFinite() && it in min..max }
            val electric=value("batteryRange",5000.0)
            val total=value("totalRange",5000.0)
            return LauncherTelemetry(value("battery",100.0),value("fuel",100.0),value("rpm",20000.0),
                value("instantFuel",100.0),value("distance",9999999.0),value("minutes",9999999.0),
                value("averageFuel",100.0),value("averagePower",200.0,-20.0),electric,total)
        }
    }
}
