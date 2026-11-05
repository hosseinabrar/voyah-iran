package com.abrar.raha.intent

/** Same span/target policy as command_evidence.py; parity is covered by shared fixtures. */
object RahaCommandEvidence {
    private val entities=linkedMapOf(
        "wireless_charger" to listOf("شارژر وایرلس","شارژ وایرلس","وایرلس شارژر","شارژر موبایل","شارژ گوشی","گوشیمو","گوشیم","گوشی","موبایلم","موبایل","شارژر"),
        "charge_port" to listOf("پورت شارژر","پورت شارژ","در شارژر","درب شارژر","درگاه شارژ","درب شارژ","در شارژ"),
        "scheduled_charge" to listOf("شارژ زمانبندی شده","شارژ زمان بندی شده","زمانبندی شارژ","زمان بندی شارژ","شارژ برنامه ریزی شده","شارژ سر ساعت","اسکجول شارژ","اسک جول شارژ","اسکجولد شارژ"),
        "easy_entry" to listOf("ورود آسان","ورود راحت","ایزی انتری","ایزی اینتری","easy entry"),
        "easy_exit" to listOf("خروج آسان","خروج راحت","ایزی اگزیت","easy exit"),
        "sunroof" to listOf("سانروف","سان روف","سقف شیشه ای","سقف"),
        "sunshade" to listOf("پرده سانروف","پرده سقف","پرده","سایبان","سان شید","سانشید","کاور سانروف","کاور سقف","کاور"),
        "window" to listOf("شیشه","پنجره"),
        "seat" to listOf("صندلی","صندلیم","صندلی من","نشیمن","پشتم","کمرم","بدنم","عضلاتم"),
        "seat_heat" to listOf("گرمکن صندلی","گرم کن صندلی","گرمایش صندلی","گرمکن"),
        "seat_cool" to listOf("سردکن صندلی","سرد کن صندلی","تهویه صندلی","سردکن"),
        "seat_massage" to listOf("ماساژور صندلی","ماساژ صندلی","ماساژور","ماساژ"),
        "climate" to listOf("تهویه","کولر","بخاری","دمای کابین","دمای اتاق","کابین","اتاق"),
        "fan" to listOf("فن کولر","فن","سرعت فن"),
        "audio" to listOf("بلندی صدا","صدای موزیک","صدا","موزیک","موسیقی","آهنگ","مولتی مدیا"),
        "fragrance" to listOf("رایحه","خوشبو کننده","خوشبوکننده"),
        "doors" to listOf("قفل درها","درب","درها","در ماشین","در خودرو"),
        "tailgate" to listOf("در صندوق","درب صندوق","صندوق عقب","صندوق"),
        "mirror" to listOf("آینه","آینه ها"),
        "display" to listOf("نمایشگر","صفحه نمایش","مانیتور"),
        "ambient" to listOf("نورپردازی داخلی","نورپردازی داخل کابین","نورپردازی کابین","نورپردازی","نور داخل کابین","نور داخل اتاق","نور داخل ماشین","نور داخل خودرو","نور داخل","نور کابین","نور اتاق"),
        "battery" to listOf("باتری","شارژ باتری")
    )
    private val capabilities=linkedMapOf(
        "wireless_charger" to listOf("SET_WIRELESS_CHARGER"),
        "charge_port" to listOf("OPEN_CHARGE_PORT"),
        "scheduled_charge" to listOf("SET_SCHEDULED_CHARGING"),
        "easy_entry" to listOf("SET_EASY_ENTRY"),
        "easy_exit" to listOf("SET_EASY_EXIT"),
        "sunroof" to listOf("VENT_SUNROOF","OPEN_SUNROOF","CLOSE_SUNROOF","SET_SUNROOF_POSITION","ADJUST_SUNROOF_POSITION"),
        "sunshade" to listOf("SET_SUNSHADE_POSITION","ADJUST_SUNSHADE_POSITION"),
        "window" to listOf("OPEN_WINDOW","CLOSE_WINDOW","SET_WINDOW_POSITION","ADJUST_WINDOW_POSITION","VENT_WINDOW","SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"),
        "seat" to listOf("SET_SEAT_","ADJUST_SEAT_","SAVE_SEAT_POSITION","RECALL_SEAT_POSITION"),
        "seat_heat" to listOf("SET_SEAT_HEATING","ADJUST_SEAT_HEATING"),
        "seat_cool" to listOf("SET_SEAT_VENTILATION","ADJUST_SEAT_VENTILATION"),
        "seat_massage" to listOf("SET_SEAT_MASSAGE","SET_SEAT_MASSAGE_PATTERN","ADJUST_SEAT_MASSAGE"),
        "climate" to listOf("SET_CLIMATE_","SET_AC_POWER","SET_MAX_COOLING","SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE","SET_FAN_SPEED","ADJUST_FAN_SPEED","SET_AIRFLOW_DIRECTION","SET_AIR_RECIRCULATION","SET_FRONT_CLIMATE_SYNC"),
        "fan" to listOf("SET_FAN_SPEED","ADJUST_FAN_SPEED"),
        "audio" to listOf("SET_AUDIO_","ADJUST_AUDIO_","PLAY_MEDIA","PAUSE_MEDIA","NEXT_MEDIA","PREVIOUS_MEDIA","OPEN_DEFAULT_MUSIC","OPEN_OEM_RADIO","SET_AMBIENT_LIGHT_MODE"),
        "fragrance" to listOf("SET_FRAGRANCE_","ADJUST_FRAGRANCE_","CONFIGURE_FRAGRANCE"),
        "doors" to listOf("SET_DOOR_","SET_CHILD_LOCK"),
        "tailgate" to listOf("OPEN_TAILGATE","CLOSE_TAILGATE"),
        "mirror" to listOf("SET_MIRROR_FOLD"),
        "display" to listOf("SET_PASSENGER_DISPLAY_POWER","SET_DISPLAY_","SWAP_DISPLAY_APPS","SET_REAR_DISPLAY_LOCK"),
        "ambient" to listOf("SET_AMBIENT_LIGHT_","ADJUST_AMBIENT_LIGHT_"),
        "battery" to listOf("SET_BATTERY_","SET_ENERGY_STRATEGY","OPEN_CHARGE_PORT","STOP_CHARGING","SET_SCHEDULED_CHARGING")
    )
    private val operations=linkedMapOf(
        "heat" to listOf("گرم تر کن","گرم تر","گرم","گرم کن","گرمکن کن","گرم کردن","گرمش کن"),
        "cool" to listOf("خنک کن","سرد کن","خنکش کن","سردش کن"),
        "open" to listOf("باز کن","بازش کن","باز کردن","باز"),
        "close" to listOf("بسته کن","ببند","ببندش","بستن","بسته"),
        "on" to listOf("روشن کن","روشنش کن","فعال کن","روشن"),
        "off" to listOf("خاموش کن","خاموشش کن","غیر فعال کن","غیرفعال کن","خاموش"),
        "massage" to listOf("بمال","ماساژ بده")
    )
    private const val left="(?<![\\p{L}\\p{M}\\p{N}_])"
    private const val right="(?![\\p{L}\\p{M}\\p{N}_])"
    private fun pattern(s:String)=Regex(left+Regex.escape(s)+right)
    private fun matches(a:String,c:List<String>)=c.any { a==it || it.endsWith('_') && a.startsWith(it) }
    private data class Span(val kind:String,val text:String,val start:Int,val end:Int)
    private fun spans(t:String,v:Map<String,List<String>>):List<Span> {
        val all=v.flatMap { (k,aliases)->aliases.flatMap { a->pattern(a).findAll(t).map { Span(k,it.value,it.range.first,it.range.last+1) }.toList() } }
        val selected=mutableListOf<Span>()
        for(s in all.sortedWith(compareBy<Span>{-(it.end-it.start)}.thenBy{it.start}))
            if(selected.none { s.start<it.end && it.start<s.end })selected.add(s)
        return selected.sortedBy{it.start}
    }
    fun check(original:String,action:String,params:Map<String,Any>):String? {
        val text=RahaCommandClauses.clean(original.replace('\u200c',' ').replace('ي','ی').replace('ك','ک').trim().replace(Regex("\\s+")," "))
        if(action=="UNKNOWN")return null
        val targets=spans(text,entities)
        val ops=spans(text,operations).filter { s->targets.none { s.start<it.end && it.start<s.end } }
        fun reject()="برداشت فرمان با هدف یا عمل گفته‌شده سازگار نیست"
        for(t in targets) {
            if(t.kind=="climate" && action=="VENT_WINDOW" && targets.any{it.kind in setOf("window","sunroof")})continue
            if(t.kind=="sunroof" && action=="VENT_WINDOW" && params["window"] in setOf("sunroof","allWithSunroof"))continue
            if(!matches(action,capabilities.getValue(t.kind)))return reject()
        }
        val kinds=ops.map{it.kind}.toSet()
        if(listOf(setOf("open","close"),setOf("on","off"),setOf("heat","cool")).any{kinds.containsAll(it)})return reject()
        if("heat" in kinds && !matches(action,listOf("SET_SEAT_HEATING","ADJUST_SEAT_HEATING","SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE","SET_CLIMATE_POWER","SET_BATTERY_PRECONDITIONING")))return reject()
        if("cool" in kinds && !matches(action,listOf("SET_SEAT_VENTILATION","ADJUST_SEAT_VENTILATION","SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE","SET_CLIMATE_POWER","SET_AC_POWER","SET_MAX_COOLING")))return reject()
        if("massage" in kinds && !matches(action,listOf("SET_SEAT_MASSAGE","ADJUST_SEAT_MASSAGE")))return reject()
        if("open" in kinds && action.startsWith("CLOSE_") || "close" in kinds && action.startsWith("OPEN_"))return reject()
        if(kinds.any{it in setOf("open","close")} && !matches(action,listOf("OPEN_","CLOSE_","SET_SUNROOF_POSITION","ADJUST_SUNROOF_POSITION","SET_SUNSHADE_POSITION","ADJUST_SUNSHADE_POSITION","SET_WINDOW_POSITION","ADJUST_WINDOW_POSITION","SET_DOOR_LOCK","SET_MIRROR_FOLD")))return reject()
        if("enabled" in params) {
            if("off" in kinds && params["enabled"]==true || "on" in kinds && params["enabled"]==false)return reject()
            if(kinds.any{it in setOf("heat","cool","massage")} && params["enabled"]==false)return reject()
        }
        val delta=(params["delta"] as? Number)?.toDouble()
        if(action=="ADJUST_CABIN_TEMPERATURE" && delta!=null && ("heat" in kinds && delta<=0 || "cool" in kinds && delta>=0))return reject()
        val percent=(params["percent"] as? Number)?.toDouble()
        val middle=Regex(left+"(?:نصف|نصفه|نیم|نیمه|وسط|متوسط|میانه)"+right).containsMatchIn(text)
        if(percent!=null && !middle && ("close" in kinds && percent!=0.0 || "open" in kinds && percent==0.0))return reject()
        if(targets.any{it.kind in setOf("sunroof","window","sunshade")} && kinds.any{it in setOf("on","off")} && !action.endsWith("DEFROSTER"))return reject()
        val physical=setOf("sunroof","sunshade","window","seat","seat_heat","seat_cool","seat_massage")
        if(physical.any{matches(action,capabilities.getValue(it))}) {
            val marker=Regex(left+"(?:رو|را)"+right).find(text)
            if(marker!=null && text.substring(0,marker.range.first).isNotBlank() && targets.none{it.end<=marker.range.first})return reject()
            if(targets.any{it.kind in physical}) {
                val remaining=text.toCharArray()
                for(s in targets+ops)for(i in s.start until s.end)remaining[i]=' '
                var residual=String(remaining)
                val fillers="رها لطفا لطفاً رو را کن کنید بده بکن بکنید بذار بگذار تنظیم تا روی به برام برای من کمی کم بیشتر کمتر زیاد بالا پایین ببر بیار بکش راننده شاگرد همراه چپ راست جلو عقب پشت عقبی جلویی سمت طرف همه هر دو نصف نصفه نیمه کامل کاملا کاملاً ته حداکثر حداقل درصد درجه سطح پله یکی یه یک دو سه چهار پنج شش شیش هفت هشت نه ده بیست سی چهل پنجاه شصت هفتاد هشتاد نود صد و صفر میشه میتونی می تونی بازش بسته اش آخر لای کناری بغل بغلی ها های تمام بیشترین کمترین ونت حافظه مموری موقعیت ذخیره بازیابی لود شدت وسط متوسط میانه".split(' ').toMutableSet()
                for(p in listOf("از تو می خوام","می خوام","قرار بگیره","باید","باشه","جای بغلی","تو حالت","از کار بنداز"))residual=pattern(p).replace(residual," ")
                if(matches(action,listOf("RECALL_SEAT_POSITION","SAVE_SEAT_POSITION","SET_SEAT_","ADJUST_SEAT_")))fillers.addAll("تو در وضعیت حالت شماره کمک".split(' '))
                residual=residual.replace(Regex("\\s+")," ")
                if(action=="VENT_WINDOW")fillers.add("حالت")
                if(action in setOf("SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"))fillers.addAll("مه بخار گرفته داره بره".split(' '))
                if(action in setOf("SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"))for(p in listOf("مه رو بره","مه گرفته","مه داره","مه بره","بخار گرفته","بخار داره","بخار بره"))residual=pattern(p).replace(residual," ")
                if(residual.trim().split(Regex("\\s+")).any{it.isNotEmpty() && it !in fillers && !it.matches(Regex("[\\d۰-۹٠-٩]+[.,٫]?[\\d۰-۹٠-٩]*"))})return reject()
            }
            if(targets.isEmpty() && action in setOf("SET_SEAT_HEATING","SET_SEAT_VENTILATION")) {
                val remaining=text.toCharArray();for(s in ops)for(i in s.start until s.end)remaining[i]=' '
                val allowed=setOf("لطفا","لطفاً","رها","یه","کم","یکم","یک","مقدار","کن")
                if(ops.isEmpty() || String(remaining).trim().split(Regex("\\s+")).any{it.isNotEmpty() && it !in allowed})return reject()
            }
        }
        return null
    }
}
