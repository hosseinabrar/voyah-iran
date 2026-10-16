package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject

/** Feedback symbols, not CAN request values (DUAL, blower and seat switches differ). */
internal class PassengerComfortCache {
    private val selections=VehicleSelectionCache(JSONObject())
    val display:JSONObject get()=selections.display
    fun accept(value:JSONObject)=selections.accept(JSONObject(value.toString()))
    fun stale()=selections.updateActual{JSONObject(it.toString()).put("fresh",false)}
    fun pending(r:VehicleSelectionCache.Request<JSONObject>)=selections.pending(r)
    fun finish(r:VehicleSelectionCache.Request<JSONObject>)=selections.finish(r)
    fun request(op:String,value:String):VehicleSelectionCache.Request<JSONObject> {
        val fields=feedback(op,value)
        return selections.request(op,{s->JSONObject(s.toString()).apply{fields.forEach{(k,v)->put(k,v)}}},
            {s->s.optBoolean("fresh") && fields.all{(k,v)->s.has(k) && !s.isNull(k) && s.optInt(k)==v}})
    }
    companion object {
        fun feedback(op:String,value:String):Map<String,Int> {
            val n=value.toIntOrNull() ?: 0
            val field=mapOf("driver-temp" to "getAirLeftTemperature","passenger-temp" to "getAirRightTemperature",
                "fan" to "getAirWindSpeed","power" to "getAirSWStatus","ac" to "getAirACStatus",
                "auto" to "getAirMode","front_defrost" to "getAirFrontWindowDefogger",
                "rear_defrost" to "getAirRearWindowDefogger","recirculation" to "getAirCirculationMode",
                "intensity" to "IVI_FRAG_CONCERNTION_FB","duration" to "FCM_DURATION_CONTROL")[op]
            if(field!=null)return mapOf(field to n)
            return when(op){
                "sync"->mapOf("getAirDUALStatus" to if(n==1)0 else 1)
                "airflow"->mapOf("getAirSupplyStatus" to mapOf("face" to 0,"faceAndFeet" to 1,"feet" to 2,
                    "windshieldAndFeet" to 3,"windshield" to 4,"all" to 6).getValue(value))
                "scent"->if(n==0)mapOf("FCM_SW_REQ_FB" to 1) else mapOf("FCM_SW_REQ_FB" to 2,"IVI_FRAG_TASTE_FB" to n)
                else->{
                    val side=if(op.substringBefore('-')=="driver")"LEFT" else "RIGHT"
                    val feature=op.substringAfter('-')
                    if(feature=="mode")mapOf("FRONT_SEAT_MASS_COMMAND_$side" to n)
                    else {
                        val type=mapOf("vent" to "VENTILATION","heat" to "HEATING","massage" to "MASS").getValue(feature)
                        val switch="FRONT_SEAT_${type}_SWITCH_$side"
                        if(n==0)mapOf(switch to 1) else mapOf(switch to 2,
                            "FRONT_SEAT_${type}_${if(type=="MASS")"INTEN" else "COMMAND"}_$side" to n)
                    }
                }
            }
        }
    }
}
