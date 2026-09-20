package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Independent intent extraction; OEM menu availability is checked at execution time. */
internal object RahaPhaseSevenRecognizer {
    val toggles = linkedMapOf(
        RahaAction.SET_DOOR_OPEN_WARNING to "هشدار باز کردن در|هشدار باز شدن در|هشدار باز شدن درها|هشدار در|اخطار در|dow",
        RahaAction.SET_FORWARD_COLLISION_WARNING to "هشدار برخورد جلو|اخطار برخورد جلو|هشدار تصادف جلو|هشدار تصادف از جلو|هشدار برخورد از جلو|fcw",
        RahaAction.SET_REAR_CROSS_TRAFFIC_WARNING to "هشدار ترافیک عقب|هشدار ترافیک متقاطع عقب|هشدار عبور از عقب|هشدار عبور خودرو از عقب|rcta|rctw",
        RahaAction.SET_REAR_COLLISION_WARNING to "هشدار برخورد عقب|اخطار برخورد عقب|هشدار تصادف عقب|هشدار برخورد از عقب|rcw",
        RahaAction.SET_AUTOMATIC_EMERGENCY_BRAKING to "ترمز اضطراری خودکار|ترمز اضطراری اتوماتیک|ترمز اضطراری|ترمز خودکار اضطراری|aeb",
        RahaAction.SET_BLIND_SPOT_MONITORING to "پایش نقطه کور|هشدار نقطه کور|تشخیص نقطه کور|نقطه کور|bsd|bsm",
        RahaAction.SET_DRIVER_MONITORING to "پایش خستگی راننده|تشخیص خستگی راننده|هشدار خستگی راننده|پایش راننده|پایش خستگی|تشخیص خستگی|هشدار خستگی|پایش حواس پرتی|هشدار حواس پرتی|حواس پرتی راننده|dms",
        RahaAction.SET_DRIVER_GESTURES to "تشخیص حرکت دست راننده|تشخیص حرکت دست|تشخیص حرکات دست|کنترل با حرکت دست|کنترل حرکتی|ژست راننده|ژست دست",
        RahaAction.SET_LEAD_VEHICLE_DEPARTURE_ALERT to "هشدار شروع حرکت خودروی جلو|هشدار حرکت خودروی جلو|هشدار حرکت ماشین جلویی|هشدار حرکت ماشین جلو|هشدار حرکت جلویی|یادآوری حرکت خودروی جلو|fvsr",
        RahaAction.SET_FRONT_CROSS_TRAFFIC_WARNING to "هشدار ترافیک متقاطع جلو|هشدار ترافیک جلو|هشدار عبور از جلو|هشدار عبور خودرو از جلو|fcta",
        RahaAction.SET_TRAFFIC_SIGN_RECOGNITION to "شناسایی تابلوها|تشخیص تابلوها|شناسایی تابلو|تشخیص تابلو|تابلو خوان|تابلوخوان|تشخیص علائم راهنمایی|تشخیص تابلوهای راهنمایی|tsr"
    )
    private const val lane="کمک حفظ خط|حفظ خط|کمک بین خطوط|کمک حرکت بین خطوط|نگهداری بین خطوط|بین خطوط|lka"
    private const val display="نمایشگر|مانیتور|صفحه نمایش|صفحه"
    private const val sound="صدای|صدا|ولوم|بلندی صدای|بلندی صدا|حجم صدای|حجم صدا"
    private const val media="موزیک|موسیقی|آهنگ|ترانه|رسانه|پخش"
    val vocabulary = toggles.values + listOf(lane,display,sound,media,
        "هشدار خروج از خط|صدایی|صوتی|لرزش|لرزشی|ویبره|حفظ|مرکز خط|وسط خط|هشدار|اخطار|کمک|حساسیت|پایش|خستگی|حواس پرتی|متقاطع|تابلوها|جلویی|حرکت|دست|ژست",
        "روشنایی|نور|شفافیت|روز|شب|دارک|لایت|تم|خودکار|اتوماتیک|مرکزی|وسط|شاگرد|راننده|پشت آمپر|کیلومتر|عقب|همه|درصد|نصف|قفل",
        "مدیا|رسانه|مکالمه|تماس|تلفن|ناوبری|مسیریاب|دستیار صوتی|سیستم|سیستمی|میوت|آن میوت|بی صدا|باصدا|صدا دار|ساکت|پلی|پاز|مکث|توقف|بعدی|قبلی|بعد|قبل|برو|ادامه|دوباره|بخون|بخونه",
        "دوربین|کمرا|عکس|عکاسی|عکس بگیر|عکس بگیرید|فیلم|فیلمبرداری|فیلم برداری|ویدیو|ضبط|شروع|پایان|تمام|تموم|متوقف|بگیر|بگیرید|کابین")

    fun recognize(text: String): Recognition.Matched? {
        var local=text.replace("بی صدا", "میوت")
        for((word,n) in mapOf("چهل" to 40,"پنجاه" to 50,"شصت" to 60,"هفتاد" to 70,"هشتاد" to 80,"نود" to 90,"صد" to 100))
            local=local.replace(Regex("(?<!\\S)$word(?!\\S)"),n.toString())
        local=local.replace(Regex("(?<!\\S)([3-9]0) و ([1-9])(?!\\S)")) {(it.groupValues[1].toInt()+it.groupValues[2].toInt()).toString()}
        val found=mutableListOf<VehicleCommand>()
        fun rule(action:RahaAction, body:RahaCommandRecognizer.Scan.()->Map<String,Any>?) {
            val scan=RahaCommandRecognizer.Scan(local)
            val params=scan.body()?:return
            while(scan.take("سیستم|خودرو|ماشین")) { }
            if(scan.finished())found+=VehicleCommand(action.name,action.name,params,describe(action,params))
        }
        for((action,names) in toggles)rule(action) {
            if(!take(names))return@rule null
            val sensitive=take("حساسیت")
            if(sensitive) {
                if(action !in setOf(RahaAction.SET_FORWARD_COLLISION_WARNING,RahaAction.SET_DRIVER_MONITORING))return@rule null
                val bound=levelBound()
                val level=if(bound!=null)bound else if(take("متوسط|میانه|مدیوم|وسط"))"medium" else if(take("بالا|زیاد|زیادتر|بیشتر"))"max" else if(take("پایین|کم|کمتر"))"min" else return@rule null
                mapOf("sensitivity" to level)
            } else mapOf("enabled" to (power()?:return@rule null))
        }
        rule(RahaAction.SET_LANE_SUPPORT_MODE) {
            if(!take(lane))return@rule null
            val on=power()
            val mode=when {
                take("مرکز|وسط|مرکز خط|وسط خط") -> "centering"
                take("هشدار|اخطار|فقط هشدار") -> "warning"
                on==false -> "off"
                on==true || take("نگهدار|نگه دار|حفظ کن|حفظ") -> "keeping"
                else -> return@rule null
            }
            mapOf("mode" to mode)
        }
        rule(RahaAction.SET_LANE_DEPARTURE_ALERT_TYPE) {
            if(!take("هشدار خروج از خط|اخطار خروج از خط|هشدار خط|هشدار بین خطوط"))return@rule null
            val type=if(take("لرزش|لرزشی|ویبره|ویبره ای"))"vibration" else if(take("صدا|صوتی|صدایی|بوق"))"voice" else return@rule null
            mapOf("type" to type)
        }
        rule(RahaAction.SET_REAR_DISPLAY_LOCK) {
            if(!take(display)||!take("عقب|پشت"))return@rule null
            val unlock=take("باز|بردار|برداشتن|آزاد")
            if(!take("قفل"))return@rule null
            val enabled=power()
            mapOf("enabled" to (enabled?:!unlock))
        }
        rule(RahaAction.SET_DISPLAY_THEME_MODE) {
            if(!take(display))return@rule null
            take("تم|روشنایی|نور")
            val mode=if(take("روز|روشن|لایت"))"day" else if(take("شب|تاریک|دارک"))"night" else if(take("خودکار|اتوماتیک|اتومات"))"auto" else return@rule null
            mapOf("mode" to mode)
        }
        rule(RahaAction.SET_DISPLAY_BRIGHTNESS) {
            if(!take(display))return@rule null
            take("روشنایی|نور")
            val target=when {take("همه")->"all";take("شاگرد|راست")->"passenger";take("پشت آمپر|کیلومتر")->"cluster";else->{take("راننده|مرکزی|وسط|چپ");"central"}}
            val bound=levelBound(); val half=take("نصف|نیمه");take("درصد")
            val n=number();val delta=direction()
            when {bound!=null->mapOf("display" to target,"percent" to if(bound=="max")100 else 0)
                half->mapOf("display" to target,"percent" to 50)
                n!=null && delta==null && n%1==0.0 && n in 0.0..100.0->mapOf("display" to target,"percent" to n.toInt())
                n==null && delta!=null->mapOf("display" to target,"deltaPercent" to delta*10)
                else->null}
        }
        fun RahaCommandRecognizer.Scan.channel():String = when {
            take("سیستم|سیستمی")->"system"
            take("مکالمه|تماس|تلفن")->"call"
            take("ناوبری|مسیریاب|نقشه")->"navigation"
            take("دستیار صوتی|دستیار|رها")->"voice"
            else->{take("مدیا|رسانه|موزیک|موسیقی|آهنگ");"media"}
        }
        for(action in listOf(RahaAction.SET_AUDIO_VOLUME,RahaAction.ADJUST_AUDIO_VOLUME,RahaAction.SET_AUDIO_MUTE))rule(action) {
            val noun=take(sound)
            val channel=channel()
            if(action==RahaAction.SET_AUDIO_MUTE) {
                val unmute=take("آن میوت|صدا دار|باصدا|صدا دار کردن")
                val mute=take("بی صدا|میوت|ساکت")
                val on=power()
                if(!noun&&!mute&&!unmute)return@rule null
                if(mute&&unmute)return@rule null
                val enabled=if(unmute)false else if(mute)on!=false else if(on!=null)!on else return@rule null
                mapOf("channel" to channel,"enabled" to enabled)
            } else {
                if(!noun)return@rule null
                val bound=levelBound();val n=number();val delta=direction()
                if(action==RahaAction.SET_AUDIO_VOLUME) {
                    when {bound!=null->mapOf("channel" to channel,"level" to bound)
                        n!=null&&delta==null&&n%1==0.0&&n in 0.0..30.0->mapOf("channel" to channel,"level" to n.toInt())
                        else->null}
                } else if(bound==null&&n==null&&delta!=null)mapOf("channel" to channel,"delta" to delta) else null
            }
        }
        for(action in listOf(RahaAction.PLAY_MEDIA,RahaAction.PAUSE_MEDIA,RahaAction.NEXT_MEDIA,RahaAction.PREVIOUS_MEDIA))rule(action) {
            val verb=when(action) {
                RahaAction.PLAY_MEDIA->take("پلی|ادامه|بخون|بخونه|شروع")
                RahaAction.PAUSE_MEDIA->take("پاز|مکث|متوقف|توقف|قطع")
                RahaAction.NEXT_MEDIA->take("بعدی|بعد")
                else->take("قبلی|قبل")
            }
            val noun=take(media);val play=take("پخش");val on=power()
            if(!verb && !(action==RahaAction.PLAY_MEDIA&&noun&&(play||on==true)) && !(action==RahaAction.PAUSE_MEDIA&&noun&&on==false))return@rule null
            take("برگرد|برو|بزن|دوباره|بیار|کن|بشه|بگیر")
            mapOf()
        }
        rule(RahaAction.OPEN_CAMERA) {
            if(!take("دوربین|کمرا")||power()!=true)return@rule null
            mapOf("view" to "default")
        }
        rule(RahaAction.TAKE_PHOTO) {
            if(!take("عکس|عکاسی"))return@rule null
            if(!take("بگیر|بگیرید|بگیرین|گرفتن|بنداز|بردار|شروع|ثبت")&&!hasRequest())return@rule null
            take("دوربین|کمرا")
            mapOf("view" to "default")
        }
        for(start in listOf(true,false))rule(if(start)RahaAction.START_VIDEO_RECORDING else RahaAction.STOP_VIDEO_RECORDING) {
            if(!take("فیلم برداری|فیلمبرداری|ویدیو|فیلم|ضبط"))return@rule null
            val stop=take("توقف|متوقف|قطع|پایان|تموم|تمام|بس|ببند")
            val go=take("شروع|بگیر|بگیرید|بگیرین|ضبط")
            val on=power()
            if(start) {if(stop||on==false||(!go&&on!=true))return@rule null}
            else if(!stop&&on!=false)return@rule null
            take("ضبط|دوربین|کردن|کن")
            if(start)mapOf("view" to "default") else mapOf()
        }
        val unique=found.distinctBy {it.action to it.parameters}
        return unique.singleOrNull()?.let {Recognition.Matched(it)}
    }
    private fun describe(action:RahaAction,p:Map<String,Any>): String {
        fun label(value:Any?) = mapOf("min" to "حداقل", "medium" to "متوسط", "max" to "حداکثر",
            "central" to "مرکزی", "passenger" to "شاگرد", "cluster" to "پشت آمپر", "all" to "همه",
            "media" to "موسیقی", "call" to "تماس", "navigation" to "مسیریاب", "voice" to "دستیار صوتی", "system" to "سیستم",
            "day" to "روز", "night" to "شب", "auto" to "خودکار", "off" to "خاموش", "warning" to "هشدار",
            "keeping" to "حفظ خط", "centering" to "مرکز خط", "vibration" to "لرزش", "voiceAlert" to "صدا")[value] ?: value.toString()
        return when(action) {
        RahaAction.PLAY_MEDIA->"پخش رسانه";RahaAction.PAUSE_MEDIA->"توقف موقت رسانه";RahaAction.NEXT_MEDIA->"رسانه بعدی";RahaAction.PREVIOUS_MEDIA->"رسانه قبلی"
        RahaAction.OPEN_CAMERA->"باز کردن دوربین";RahaAction.TAKE_PHOTO->"گرفتن عکس";RahaAction.START_VIDEO_RECORDING->"شروع فیلم‌برداری";RahaAction.STOP_VIDEO_RECORDING->"توقف فیلم‌برداری"
        RahaAction.SET_LANE_SUPPORT_MODE->"کمک حفظ خط: ${label(p["mode"])}"
        RahaAction.SET_LANE_DEPARTURE_ALERT_TYPE->"هشدار خروج از خط: ${if(p["type"]=="voice")"صدا" else "لرزش"}"
        RahaAction.SET_REAR_DISPLAY_LOCK->if(p["enabled"]==true)"قفل نمایشگر عقب" else "باز کردن قفل نمایشگر عقب"
        RahaAction.SET_DISPLAY_THEME_MODE->"حالت نمایشگر: ${label(p["mode"])}"
        RahaAction.SET_DISPLAY_BRIGHTNESS->"روشنایی نمایشگر ${label(p["display"])}: "+if(p.containsKey("percent"))"${p["percent"]} درصد" else if((p["deltaPercent"] as Int)>0)"بیشتر" else "کمتر"
        RahaAction.SET_AUDIO_VOLUME->"صدای ${label(p["channel"])}: ${label(p["level"])}"
        RahaAction.ADJUST_AUDIO_VOLUME->"صدای ${label(p["channel"])}: ${if(p["delta"]==1)"بیشتر" else "کمتر"}"
        RahaAction.SET_AUDIO_MUTE->"صدای ${label(p["channel"])}: ${if(p["enabled"]==true)"بی‌صدا" else "صدادار"}"
        else->"${toggles[action]?.substringBefore('|')}: "+if(p.containsKey("sensitivity"))"حساسیت ${label(p["sensitivity"])}" else if(p["enabled"]==true)"فعال" else "غیرفعال"
        }
    }
}
