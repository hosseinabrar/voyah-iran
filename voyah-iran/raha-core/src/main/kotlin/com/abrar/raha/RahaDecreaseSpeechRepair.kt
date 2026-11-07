package com.abrar.raha

import com.abrar.voicecommands.Recognition

/** Vosk کم -> کن confusion, restricted to the amount/operator position. */
internal object RahaDecreaseSpeechRepair {
    private val explicitVerb=Regex("(?:^| )(?:گرم|سرد|خنک|باز|روشن|خاموش|فعال|غیرفعال|اکتیو|زیاد|بیشتر|کم|کمتر|تندتر|کندتر|تنظیم|هماهنگ|سینک|قفل|شارژ|پخش|ذخیره|بازیابی|آپدیت|شروع|متوقف|کنسل|کنترل|رانندگی)$")
    fun candidates(raw:String):List<String> {
        val text=RahaInputText.spaces(raw)
        val result=mutableListOf<String>()
        if(text.endsWith(" کن کن")) return listOf(text.removeSuffix(" کن کن")+" کم کن")
        if(text.endsWith(" کن")) {
            val prefix=text.removeSuffix(" کن")
            if(!explicitVerb.containsMatchIn(prefix)) result+="$prefix کم کن"
        }
        val beforeSetter=Regex("(?<= )کن (?=تنظیم کن$|بذار$|بگذار$)")
        if(beforeSetter.containsMatchIn(text)) result+=beforeSetter.replace(text,"کم ")
        return result.distinct()
    }
    fun isDecrease(result:Recognition):Boolean {
        val c=(result as? Recognition.Matched)?.command ?: return false
        val lowLevel=c.parameters["level"]
        val explicitLow=c.action in setOf("SET_SEAT_HEATING","SET_SEAT_VENTILATION","SET_SEAT_MASSAGE","SET_FAN_SPEED","SET_WIPER_SENSITIVITY","SET_REGENERATION_LEVEL","SET_FRAGRANCE_INTENSITY") &&
            (lowLevel in setOf("low","min") || (lowLevel as? Number)?.toDouble()==1.0)
        return explicitLow || (c.action.startsWith("ADJUST_") && (c.parameters["delta"] as? Number)?.toDouble()?.let {it<0}==true) ||
            (c.action=="SET_DISPLAY_BRIGHTNESS" && (c.parameters["deltaPercent"] as? Number)?.toDouble()?.let {it<0}==true)
    }
}
