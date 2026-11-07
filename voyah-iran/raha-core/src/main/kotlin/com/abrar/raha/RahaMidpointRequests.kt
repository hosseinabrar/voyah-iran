package com.abrar.raha

import com.abrar.voicecommands.Recognition

/** Absolute midpoints use each control's scale; never halve its current value. */
internal object RahaMidpointRequests {
    private val middle=Regex("(?<!\\S)(?:متوسط|وسط|میانه)(?!\\S)")
    private val centralDisplay=Regex("(?<!\\S)(نمایشگر|مانیتور|صفحه نمایش) وسط(?!\\S)")
    private val values=mapOf(
        "SET_AUDIO_VOLUME" to ("level" to 15),
        "SET_AMBIENT_LIGHT_BRIGHTNESS" to ("percent" to 50),
        "SET_DISPLAY_BRIGHTNESS" to ("percent" to 50),
        "SET_WINDOW_POSITION" to ("percent" to 50),
        "SET_SUNROOF_POSITION" to ("percent" to 50),
        "SET_SUNSHADE_POSITION" to ("position" to 50),
        "SET_CABIN_TEMPERATURE" to ("degreesC" to 24),
        "SET_FAN_SPEED" to ("level" to 4),
        "SET_SEAT_HEATING" to ("level" to 2),
        "SET_SEAT_VENTILATION" to ("level" to 2),
        "SET_SEAT_MASSAGE" to ("level" to 2),
        "SET_WIPER_SENSITIVITY" to ("level" to 2),
        "SET_FRAGRANCE_INTENSITY" to ("intensity" to "medium"),
        "SET_REGENERATION_LEVEL" to ("level" to "medium"),
        "SET_SUSPENSION_HEIGHT" to ("level" to "medium"),
        "SET_BATTERY_RESERVE_TARGET" to ("percent" to 50)
    )
    fun resolve(raw:String, parse:(String)->Recognition):Recognition.Matched? {
        var text=centralDisplay.replace(RahaInputText.spaces(raw)) { "${it.groupValues[1]} مرکزی" }
        if(!middle.containsMatchIn(text))return null
        text=text.replace(Regex("(?<!\\S)ذخیره باتری(?!\\S)"),"حد حفظ شارژ")
        fun valid(r:Recognition):Recognition.Matched? {
            val m=r as? Recognition.Matched ?: return null
            val (field,expected)=values[m.command.action] ?: return null
            val actual=m.command.parameters[field]
            return m.takeIf { if(expected is Number && actual is Number) expected.toDouble()==actual.toDouble() else expected==actual }
        }
        valid(parse(text))?.let { return it }
        // The existing parser still has to consume the entire request, identify the control,
        // and preserve target/side. Only the value is supplied by the midpoint table.
        val candidates=listOf("2","4","15","24","50","50 درصد","متوسط").mapNotNull { value ->
            valid(parse(middle.replace(text,value)))
        }.distinctBy { it.command.action to it.command.parameters.mapValues { (_,v) -> if(v is Number) v.toDouble() else v } }
        return candidates.singleOrNull()
    }
    fun normalizeObjectMarker(text:String):String = text.replace(
        Regex("(?<!\\S)ور(?= (?:بذار|بگذار|بزار|روی|تنظیم|قرار)(?!\\S))"),"رو")
}
