package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaPhaseSevenRecognizerTest {
    private val recognizer=RahaCommandRecognizer()
    private fun match(text:String,action:String,key:String?=null,value:Any?=null) {
        val r=recognizer.recognize(text)
        assertTrue("$text => $r",r is Recognition.Matched)
        val c=(r as Recognition.Matched).command
        assertEquals(text,action,c.action)
        if(key!=null)assertEquals(text,value,c.parameters[key])
    }
    @Test fun volumeAliasesAndExplicitSteps() {
        mapOf("ولوم رو کم  کن" to -1, "والیوم رو ۵ تا کم کن" to -5,
            "صدا رو ۵ تا زیاد کن" to 5, "ولوم صدا رو ۵ تا زیاد کن" to 5,
            "ولوم رو ببر بالا" to 1, "ولوم و بیار پایین" to -1,
            "ولوم رو ۱۰ تا بیار پایین" to -10,
            "والیوم را پنج پله کاهش دهید" to -5,
            "صدای موسیقی را سه واحد افزایش بده" to 3
        ).forEach { (text, delta) -> match(text,"ADJUST_AUDIO_VOLUME","delta",delta) }
        listOf("ولوم رو بذار رو ۱۰", "ولوم و بذار رو ۱۰", "ولوم بشه ۱۰", "والیوم را روی ده تنظیم کنید")
            .forEach { match(it,"SET_AUDIO_VOLUME","level",10) }
        listOf("صدا رو ۳۱ تا زیاد کن", "صدا رو ۰ تا کم کن", "صدا رو ۲ و ۵ تا کم کن")
            .forEach { assertFalse(it, recognizer.recognize(it) is Recognition.Matched) }
    }
    @Test fun assistantTogglesAcrossWordOrderAndFormalSpeech() {
        val cases=mapOf("هشدار باز شدن در" to "SET_DOOR_OPEN_WARNING", "هشدار برخورد جلو" to "SET_FORWARD_COLLISION_WARNING",
            "هشدار ترافیک عقب" to "SET_REAR_CROSS_TRAFFIC_WARNING", "هشدار برخورد عقب" to "SET_REAR_COLLISION_WARNING",
            "ترمز اضطراری" to "SET_AUTOMATIC_EMERGENCY_BRAKING", "نقطه کور" to "SET_BLIND_SPOT_MONITORING",
            "پایش خستگی راننده" to "SET_DRIVER_MONITORING", "تشخیص حرکت دست" to "SET_DRIVER_GESTURES",
            "هشدار حرکت ماشین جلویی" to "SET_LEAD_VEHICLE_DEPARTURE_ALERT", "هشدار ترافیک متقاطع جلو" to "SET_FRONT_CROSS_TRAFFIC_WARNING",
            "تشخیص تابلوها" to "SET_TRAFFIC_SIGN_RECOGNITION")
        cases.forEach { (noun,action)->
            listOf("$noun رو فعال کن","فعال کردن $noun","لطفا $noun را روشن کنید","$noun اکتیو","سیستم $noun روشن").forEach {match(it,action,"enabled",true)}
            listOf("$noun رو خاموش کن","غیر فعال کردن $noun","لطفا $noun را غیر فعال کنید","$noun دی اکتیو").forEach {match(it,action,"enabled",false)}
        }
    }
    @Test fun supportedModesAndSensitivities() {
        match("حساسیت هشدار برخورد جلو رو روی متوسط بذار","SET_FORWARD_COLLISION_WARNING","sensitivity","medium")
        match("حساسیت پایش خستگی راننده حداکثر","SET_DRIVER_MONITORING","sensitivity","max")
        match("حفظ خط رو فعال کن","SET_LANE_SUPPORT_MODE","enabled",true)
        match("حفظ خط رو خاموش کن","SET_LANE_SUPPORT_MODE","mode","off")
        match("حفظ خط رو بذار روی هشدار","SET_LANE_SUPPORT_MODE","mode","warning")
        match("هشدار خروج از خط رو لرزشی کن","SET_LANE_DEPARTURE_ALERT_TYPE","type","vibration")
        match("هشدار خروج از خط صوتی","SET_LANE_DEPARTURE_ALERT_TYPE","type","voice")
    }
    @Test fun displayAndAudio() {
        match("روشنایی نمایشگر رو روی 50 درصد بذار","SET_DISPLAY_BRIGHTNESS","percent",50)
        match("روشنایی نمایشگر شاگرد رو روی پنجاه درصد بذار","SET_DISPLAY_BRIGHTNESS","percent",50)
        match("نور نمایشگر را روی هفتاد و پنج درصد تنظیم کنید","SET_DISPLAY_BRIGHTNESS","percent",75)
        match("نور مانیتور شاگرد رو بیشتر کن","SET_DISPLAY_BRIGHTNESS","display","passenger")
        match("نور مانیتور رو کم کن","SET_DISPLAY_BRIGHTNESS","deltaPercent",-10)
        match("نمایشگر رو بذار روی حالت شب","SET_DISPLAY_THEME_MODE","mode","night")
        match("مانیتور اتوماتیک","SET_DISPLAY_THEME_MODE","mode","auto")
        match("نمایشگر عقب رو قفل کن","SET_REAR_DISPLAY_LOCK","enabled",true)
        match("قفل نمایشگر عقب رو باز کن","SET_REAR_DISPLAY_LOCK","enabled",false)
        match("صدای موزیک رو روی 15 بذار","SET_AUDIO_VOLUME","level",15)
        match("بلندی صدای موسیقی را روی پانزده تنظیم کنید","SET_AUDIO_VOLUME","level",15)
        match("ولوم سیستم رو تا ته زیاد کن","SET_AUDIO_VOLUME","level","max")
        match("صدا رو زیاد کن","ADJUST_AUDIO_VOLUME","delta",1)
        match("صدای مسیریاب رو کم کن","ADJUST_AUDIO_VOLUME","channel","navigation")
        match("موزیک رو بی صدا کن","SET_AUDIO_MUTE","enabled",true)
        match("صدا رو آن میوت کن","SET_AUDIO_MUTE","enabled",false)
    }
    @Test fun playbackAndCamera() {
        for(s in listOf("آهنگ رو پخش کن","پلی کن","موزیک ادامه","موسیقی را روشن کنید"))match(s,"PLAY_MEDIA")
        for(s in listOf("آهنگ رو پاز کن","موسیقی رو متوقف کن","آهنگ رو خاموش کن"))match(s,"PAUSE_MEDIA")
        match("آهنگ بعدی","NEXT_MEDIA");match("برگرد آهنگ قبلی","PREVIOUS_MEDIA")
        match("دوربین رو باز کن","OPEN_CAMERA")
        match("عکس بگیر","TAKE_PHOTO");match("لطفا عکس بگیرید","TAKE_PHOTO")
        match("فیلم بگیر","START_VIDEO_RECORDING");match("فیلمبرداری رو شروع کن","START_VIDEO_RECORDING")
        match("فیلمبرداری رو متوقف کن","STOP_VIDEO_RECORDING");match("ضبط رو قطع کن","STOP_VIDEO_RECORDING")
    }
    @Test fun adjacentTargetsRemainDistinct() {
        match("دور فن رو زیاد کن","ADJUST_FAN_SPEED")
        match("نورپردازی داخلی رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS")
        match("دوربین رو روشن کن","OPEN_CAMERA")
        match("در رو باز کن","SET_DOOR_LOCK")
        match("رایحه رو پخش کن","SET_FRAGRANCE_POWER")
        match("مصرف انرژی هوشمند","SET_ENERGY_STRATEGY")
    }
}
