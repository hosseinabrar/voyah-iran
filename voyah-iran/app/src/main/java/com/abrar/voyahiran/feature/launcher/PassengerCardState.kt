package com.abrar.voyahiran.feature.launcher

import org.json.JSONObject

/** Metadata/revisions and unrelated CAN fields never invalidate a native card. */
internal object PassengerCardState {
    fun select(source:JSONObject,card:String):JSONObject=JSONObject().apply {
        put("fresh",source.optBoolean("fresh"))
        source.keys().forEach { key ->
            val include=when(card){
                "climate"->key.startsWith("getAir")
                "seats"->key.startsWith("FRONT_SEAT_")
                "fragrance"->key.startsWith("FCM_") || key.startsWith("IVI_FRAG_")
                else->false
            }
            if(include)put(key,source.get(key))
        }
    }
}
