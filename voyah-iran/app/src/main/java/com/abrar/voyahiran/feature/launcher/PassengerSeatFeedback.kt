package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject

/** SeatFrontManager.updateStatus: remembered intensity is effective only when SWITCH == 2.
 * Missing/invalid feedback is unknown, never an active level or a writable default.
 */
internal object PassengerSeatFeedback {
    fun level(state:JSONObject,side:Int,feature:String):Int {
        if(!state.optBoolean("fresh"))return -1
        val suffix=if(side==0)"LEFT"else "RIGHT"
        if(feature=="mode")return state.optInt("FRONT_SEAT_MASS_COMMAND_$suffix",-1).takeIf{it in 1..2} ?: -1
        val type=when(feature){"vent"->"VENTILATION";"heat"->"HEATING";"massage"->"MASS";else->return -1}
        val switch=state.optInt("FRONT_SEAT_${type}_SWITCH_$suffix",-1)
        if(switch==1)return 0
        if(switch!=2)return -1
        val field=if(type=="MASS")"INTEN"else "COMMAND"
        return state.optInt("FRONT_SEAT_${type}_${field}_$suffix",-1).takeIf{it in 0..3} ?: -1
    }
}
