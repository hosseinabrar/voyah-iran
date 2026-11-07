package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Full-sentence extensions for commands introduced after 2.1.1. */
internal object OldRahaExtensions {
    private val targets=mapOf(
        "SET_EASY_ENTRY" to "ورود آسان|ورود راحت|ایزی انتری|ایزی اینتری|easy entry",
        "SET_EASY_EXIT" to "خروج آسان|خروج راحت|ایزی اگزیت|easy exit",
        "SET_SCHEDULED_CHARGING" to "شارژ زمانبندی شده|شارژ زمان بندی شده|شارژ برنامه ریزی شده|زمان بندی شارژ|زمانبندی شارژ|شارژ سر ساعت|شارژ در ساعت مشخص|اسکجول شارژ|اسک جول شارژ|scheduled charging",
        "SET_WIRELESS_CHARGER" to "شارژر|شارژ وایرلس|شارژر وایرلس|وایرلس شارژر|شارژر موبایل|شارژ گوشی|شارژر بی سیم",
        "SET_PASSENGER_DISPLAY_POWER" to "(?:نمایشگر|مانیتور) (?:سمت )?(?:راست|شاگرد|همراه)"
    )
    private val states="غیر فعال|غیرفعال|دی اکتیو|خاموش|فعال|اکتیو|روشن"
    fun recognize(text:String):Recognition? {
        fun command(action:String,p:Map<String,Any>)=Recognition.Matched(VehicleCommand(action,action,p,action))
        for((action,nouns) in targets) {
            val match=Regex("^(?:لطفا )?(?:$nouns)(?: رو| را)? (?:(?:بذار|بگذار|قرار بده) (?:تو |روی |در )?(?:حالت )?)?($states)(?: کن| بشه| باشه| بذار| قرار بده)?$").matchEntire(text) ?: continue
            return command(action,mapOf("enabled" to (match.groupValues[1] in setOf("فعال","اکتیو","روشن"))))
        }
        if(Regex("^(?:گوشی|گوشیم|گوشیمو)(?: رو| را)? شارژ کن$").matches(text))return command("SET_WIRELESS_CHARGER",mapOf("enabled" to true))
        if(Regex("^(?:ماشین رو |ماشین را )?(?:سر ساعت(?: تعیین شده)?|بر اساس زمان بندی|بر اساس زمانبندی) شارژ کن$").matches(text))return command("SET_SCHEDULED_CHARGING",mapOf("enabled" to true))
        if(Regex("^(?:هوای داخل|هوای کابین|هوای اتاق)(?: رو| را)? بچرخون$").matches(text))return command("SET_AIR_RECIRCULATION",mapOf("mode" to "internal"))
        val height=Regex("^(?:ارتفاع(?: ماشین| خودرو)?|ماشین)(?: رو| را)? (?:یکم |یه مقدار )?(?:(?:ببر|بیار) )?(بالا تر|بالاتر|پایین تر|پایینتر|زیادتر|بیشتر|کمتر|زیاد|کم|بالا|پایین)(?: کن| ببر| بیار)?$").matchEntire(text)
        if(height!=null)return command("ADJUST_SUSPENSION_HEIGHT",mapOf("delta" to if(height.groupValues[1] in setOf("پایین تر","پایینتر","کمتر","کم","پایین")) -1 else 1))
        val collision=Regex("^حساسیت (?:هشدار برخورد|برخورد جلو)(?: رو| را)? (?:یکم |یه مقدار )?(کم|کمتر|زیاد|زیادتر|بیشتر) کن$").matchEntire(text)
        if(collision!=null)return command("ADJUST_FORWARD_COLLISION_SENSITIVITY",mapOf("delta" to if(collision.groupValues[1].startsWith("کم")) -1 else 1))
        val vent=Regex("^(?:حالت )?تهویه(?: رو)? (?:برای )?(شیشه ها و سانروف|شیشه و سانروف|سانروف|شیشه ها)(?: رو| را)?(?: فعال کن| روشن کن)?$").matchEntire(text)
        if(vent!=null)return command("VENT_WINDOW",mapOf("window" to when(vent.groupValues[1]) {"سانروف"->"sunroof";"شیشه ها"->"all";else->"allWithSunroof"}))
        return null
    }
}
