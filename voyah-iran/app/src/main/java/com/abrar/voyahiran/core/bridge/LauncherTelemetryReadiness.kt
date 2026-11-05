package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.launcher.LauncherTelemetry
import org.json.JSONObject

/** OEM sentinel values and partially populated parcels are not a completed initial snapshot. */
internal object LauncherTelemetryReadiness {
    fun ready(getter:String,snapshot:JSONObject):Boolean {
        val value=LauncherTelemetry.parse(snapshot)
        return when(getter) {
            "getBatteryRemainingCapacity" -> value.battery!=null
            "getEngineSpeed" -> value.rpm!=null
            "getFuelLevel" -> value.fuel!=null && value.instantFuel!=null
            "getOdometer" -> value.batteryRange!=null && value.fuelRange!=null
            "getEnergyConsumptionInfo" -> value.distance!=null && value.minutes!=null && value.averageFuel!=null && value.averagePower!=null
            else -> false
        }
    }
}
