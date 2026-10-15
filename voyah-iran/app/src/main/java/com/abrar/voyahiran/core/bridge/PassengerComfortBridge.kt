package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import org.json.JSONObject

/** Symbolic, bounded UI commands; reuse the vehicle-tested OEM planners, not raw CAN writes. */
internal object PassengerComfortBridge {
    fun valid(op:String,value:String):Boolean = when(op) {
        "driver-temp","passenger-temp" -> value.toIntOrNull()?.let {it in 155..305 && it%5==0}==true
        "fan" -> value.toIntOrNull() in 0..7
        "power","ac","auto","sync","front_defrost","rear_defrost" -> value in listOf("0","1")
        "recirculation","duration" -> value in listOf("0","1","2")
        "scent" -> value.toIntOrNull() in 0..3
        "intensity" -> value.toIntOrNull() in 1..3
        "airflow" -> value in listOf("face","faceAndFeet","feet","windshieldAndFeet","windshield","all")
        else -> op.matches(Regex("(driver|passenger)-(vent|heat|massage|mode)")) &&
            value.toIntOrNull() in if(op.endsWith("mode"))1..2 else 0..3
    }
    fun execute(op:String,value:String):CommandResult {
        if(!BridgeSubscriptionSession.vipActive)return CommandResult(126,"VIP required")
        if(!valid(op,value))return CommandResult(126,"Unsupported comfort command")
        val p=JSONObject()
        val action=when(op) {
            "driver-temp","passenger-temp" -> {p.put("zone",op.substringBefore('-')).put("degreesC",value.toInt()/10.0);"SET_CABIN_TEMPERATURE"}
            "fan" -> return fan(value.toInt())
            "scent" -> {p.put("enabled",value!="0");if(value!="0")p.put("channel",value.toInt());"CONFIGURE_FRAGRANCE"}
            "intensity" -> {p.put("intensity",value.toInt());"SET_FRAGRANCE_INTENSITY"}
            "duration" -> {p.put("durationMinutes",listOf(0,30,60)[value.toInt()]);"SET_FRAGRANCE_DURATION"}
            "airflow" -> {p.put("direction",value);"SET_AIRFLOW_DIRECTION"}
            "recirculation" -> {p.put("mode",listOf("internal","external","auto")[value.toInt()]);"SET_AIR_RECIRCULATION"}
            "power","ac","auto","sync","front_defrost","rear_defrost" -> {
                p.put("enabled",value=="1")
                mapOf("power" to "SET_CLIMATE_POWER","ac" to "SET_AC_POWER","auto" to "SET_CLIMATE_AUTO","sync" to "SET_FRONT_CLIMATE_SYNC",
                    "front_defrost" to "SET_FRONT_DEFROSTER","rear_defrost" to "SET_REAR_DEFROSTER").getValue(op)
            }
            else -> {
                p.put("seat",op.substringBefore('-'))
                if(op.endsWith("mode")) {p.put("pattern",if(value=="1")"wave" else "rolling");"SET_SEAT_MASSAGE_PATTERN"}
                else {p.put("enabled",value!="0");if(value!="0")p.put("level",value.toInt())
                    "SET_SEAT_"+mapOf("heat" to "HEATING","vent" to "VENTILATION","massage" to "MASSAGE").getValue(op.substringAfter('-'))}
            }
        }
        return RahaVehicleBridge.execute(action,p.toString())
    }
    private fun fan(level:Int):CommandResult {
        // Planner accepts levels 1..8; zero feedback needs an explicit bounded helper.
        return RahaVehicleBridge.setLauncherFan(level)
    }
}
