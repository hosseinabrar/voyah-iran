package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaMidpointTest {
    @Test fun eachControlUsesItsOwnMidpoint() {
        val cases=listOf(
            Triple("نورپردازی داخلی رو روی %s تنظیم کن","SET_AMBIENT_LIGHT_BRIGHTNESS","percent" to 50),
            Triple("شدت نورپردازی داخلی ور بذار روی %s","SET_AMBIENT_LIGHT_BRIGHTNESS","percent" to 50),
            Triple("صدا رو روی %s تنظیم کن","SET_AUDIO_VOLUME","level" to 15),
            Triple("روشنایی نمایشگر وسط رو روی %s تنظیم کن","SET_DISPLAY_BRIGHTNESS","percent" to 50),
            Triple("دمای راننده رو روی %s تنظیم کن","SET_CABIN_TEMPERATURE","degreesC" to 24),
            Triple("فن رو روی %s تنظیم کن","SET_FAN_SPEED","level" to 4),
            Triple("گرمکن صندلی راست رو روی %s تنظیم کن","SET_SEAT_HEATING","level" to 2),
            Triple("سردکن صندلی رو روی %s تنظیم کن","SET_SEAT_VENTILATION","level" to 2),
            Triple("ماساژور رو روی %s تنظیم کن","SET_SEAT_MASSAGE","level" to 2),
            Triple("حساسیت سنسور بارون رو روی %s تنظیم کن","SET_WIPER_SENSITIVITY","level" to 2),
            Triple("شیشه راننده رو روی %s تنظیم کن","SET_WINDOW_POSITION","percent" to 50),
            Triple("سانروف رو روی %s تنظیم کن","SET_SUNROOF_POSITION","percent" to 50),
            Triple("پرده رو روی %s تنظیم کن","SET_SUNSHADE_POSITION","position" to 50),
            Triple("ارتفاع ماشین رو روی %s تنظیم کن","SET_SUSPENSION_HEIGHT","level" to "medium"),
            Triple("بازیابی انرژی رو روی %s تنظیم کن","SET_REGENERATION_LEVEL","level" to "medium"),
            Triple("شدت رایحه رو روی %s تنظیم کن","SET_FRAGRANCE_INTENSITY","intensity" to "medium"),
            Triple("ذخیره باتری رو روی %s تنظیم کن","SET_BATTERY_RESERVE_TARGET","percent" to 50)
        )
        for(parser in listOf(OldRahaRecognizer(),OldRahaRecognizer { "".byteInputStream() }))
            for((template,action,value) in cases) for(word in listOf("متوسط","وسط","میانه")) {
                val text=template.format(word);val result=parser.recognize(text)
                assertTrue("$text => $result",result is Recognition.Matched)
                val c=(result as Recognition.Matched).command
                assertEquals(text,action,c.action)
                val actual=c.parameters[value.first]
                if(value.second is Number)assertEquals(text,(value.second as Number).toDouble(),(actual as Number).toDouble(),0.0)
                else assertEquals(text,value.second,actual)
            }
    }
    @Test fun relativeAndDisplayTargetRemainDistinct() {
        val parser=OldRahaRecognizer()
        val display=parser.recognize("روشنایی نمایشگر وسط رو زیاد کن") as Recognition.Matched
        assertTrue(display.command.parameters.containsKey("deltaPercent"))
        for(text in listOf("نورپردازی داخلی رو روی متوسط و زیاد تنظیم کن","تلویزیون رو روی متوسط تنظیم کن"))
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
    }
}
