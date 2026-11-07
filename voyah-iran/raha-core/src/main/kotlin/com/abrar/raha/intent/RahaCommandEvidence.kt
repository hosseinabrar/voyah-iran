package com.abrar.raha.intent

/** Same span/target policy as command_evidence.py; parity is covered by shared fixtures. */
object RahaCommandEvidence {
    private val explicitToggleActions by lazy {
        checkNotNull(javaClass.getResourceAsStream("/raha-explicit-toggle-actions.txt")).bufferedReader().use { it.readLines().toSet() }
    }
    private val toggleState = Regex("""(?<![\p{L}\p{M}\p{N}_])(?:روشن(?:ش)?|خاموش(?:ش)?|غیر ?فعال(?:ش)?|فعال(?:ش)?|اکتیو|دی ?اکتیو|فعال سازی|غیرفعال سازی|راه بنداز|از کار بنداز|قطع کن|وصل کن|میوت|بی صدا|باصدا)(?![\p{L}\p{M}\p{N}_])""")

    private val entities=linkedMapOf(
        "defroster" to listOf("بخارزدایی","بخار زدایی","بخاری شیشه","هیتر شیشه","المنت شیشه","بخارزدای","بخار زدای","بخارزدا","بخار زدا"),
        "door_warning" to listOf("هشدار بازکردن در","هشدار بازکردن درب","هشدار بازکردن درها","هشدار باز کردن در","هشدار باز کردن درب","هشدار باز کردن درها","هشدار باز شدن در","هشدار باز شدن درب","هشدار باز شدن درها","هشدار بازشدن در","هشدار بازشدن درب","هشدار بازشدن درها"),
        "wireless_charger" to listOf("شارژر وایرلس","شارژ وایرلس","وایرلس شارژر","شارژر موبایل","شارژ گوشی","گوشیمو","گوشیم","گوشی","موبایلم","موبایل","شارژر"),
        "charge_port" to listOf("درپوش شارژر","درپوش شارژ","در پوش شارژر","در پوش شارژ","پورت شارژر","پورت شارژ","در شارژر","درب شارژر","درگاه شارژ","درب شارژ","در شارژ"),
        "scheduled_charge" to listOf("شارژ زمانبندی شده","شارژ زمان بندی شده","زمانبندی شارژ","زمان بندی شارژ","شارژ برنامه ریزی شده","شارژ سر ساعت","اسکجول شارژ","اسک جول شارژ","اسکجولد شارژ"),
        "easy_entry" to listOf("ورود آسان","ورود راحت","ایزی انتری","ایزی اینتری","easy entry"),
        "easy_exit" to listOf("خروج آسان","خروج راحت","ایزی اگزیت","easy exit"),
        "sunroof" to listOf("سانروف ماشین","سانروف اتاق","سانروف","سان روف","سقف شیشه ای","سقف"),
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
        "fuel_flap" to listOf("درب باک بنزین","در باک بنزین","درپوش باک بنزین","درب باک","در باک","درپوش باک","باک بنزین","باک"),
        "doors" to listOf("قفل درها","درب","درها","در ماشین","در خودرو"),
        "tailgate" to listOf("در صندوق","درب صندوق","صندوق عقب","صندوق"),
        "mirror" to listOf("آینه","آینه ها"),
        "display" to listOf("نمایشگر","صفحه نمایش","مانیتور"),
        "ambient" to listOf("نورپردازی داخلی","نورپردازی داخل کابین","نورپردازی کابین","نورپردازی","نور داخل کابین","نور داخل اتاق","نور داخل ماشین","نور داخل خودرو","نور داخل","نور کابین","نور اتاق"),
        "battery" to listOf("باتری","شارژ باتری")
    )
    private val capabilities=linkedMapOf(
        "defroster" to listOf("بخارزدایی","بخار زدایی","بخاری شیشه","هیتر شیشه","المنت شیشه","SET_DEFROSTER","SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"),
        "door_warning" to listOf("SET_DOOR_OPEN_WARNING"),
        "wireless_charger" to listOf("SET_WIRELESS_CHARGER"),
        "charge_port" to listOf("OPEN_CHARGE_PORT"),
        "scheduled_charge" to listOf("SET_SCHEDULED_CHARGING"),
        "easy_entry" to listOf("SET_EASY_ENTRY"),
        "easy_exit" to listOf("SET_EASY_EXIT"),
        "sunroof" to listOf("VENT_SUNROOF","OPEN_SUNROOF","CLOSE_SUNROOF","SET_SUNROOF_POSITION","ADJUST_SUNROOF_POSITION"),
        "sunshade" to listOf("SET_SUNSHADE_POSITION","ADJUST_SUNSHADE_POSITION"),
        "window" to listOf("OPEN_WINDOW","CLOSE_WINDOW","SET_WINDOW_POSITION","ADJUST_WINDOW_POSITION","VENT_WINDOW","SET_DEFROSTER","SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"),
        "seat" to listOf("SET_SEAT_","ADJUST_SEAT_","SAVE_SEAT_POSITION","RECALL_SEAT_POSITION"),
        "seat_heat" to listOf("SET_SEAT_HEATING","ADJUST_SEAT_HEATING"),
        "seat_cool" to listOf("SET_SEAT_VENTILATION","ADJUST_SEAT_VENTILATION"),
        "seat_massage" to listOf("SET_SEAT_MASSAGE","SET_SEAT_MASSAGE_PATTERN","ADJUST_SEAT_MASSAGE"),
        "climate" to listOf("SET_CLIMATE_","SET_AC_POWER","SET_MAX_COOLING","SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE","SET_FAN_SPEED","ADJUST_FAN_SPEED","SET_AIRFLOW_DIRECTION","SET_AIR_RECIRCULATION","SET_FRONT_CLIMATE_SYNC"),
        "fan" to listOf("SET_FAN_SPEED","ADJUST_FAN_SPEED"),
        "audio" to listOf("SET_AUDIO_","ADJUST_AUDIO_","PLAY_MEDIA","PAUSE_MEDIA","NEXT_MEDIA","PREVIOUS_MEDIA","OPEN_DEFAULT_MUSIC","OPEN_OEM_RADIO","SET_AMBIENT_LIGHT_MODE"),
        "fragrance" to listOf("SET_FRAGRANCE_","ADJUST_FRAGRANCE_","CONFIGURE_FRAGRANCE"),
        "fuel_flap" to listOf("OPEN_FUEL_FLAP"),
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
        "open" to listOf("باز کن","بازش کن","باز کردن","بازکردن","بازکن","باز"),
        "close" to listOf("بسته کن","ببند","ببندش","بستن","بسته"),
        "on" to listOf("روشن کن","روشنش کن","فعال کن","روشن"),
        "off" to listOf("خاموش کن","خاموشش کن","غیر فعال کن","غیرفعال کن","خاموش"),
        "massage" to listOf("بمال","ماساژ بده")
    )
    private const val left="(?<![\\p{L}\\p{M}\\p{N}_])"
    private const val right="(?![\\p{L}\\p{M}\\p{N}_])"
    private val openCharger=Regex("شارژر (?:رو |را )?باز(?:ش)? ?کن"+right)
    private val rearAddress=Regex(left+"(?:پشت(?: سر(?:ی)?)?|پست سر(?:ی)?|عقب) (?:سمت )?(راننده|چپ|شاگرد|همراه|راست)"+right)
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
        val text=RahaCommandClauses.clean(RahaWordPiece.normalize(original).replace('\u200c',' ').replace('ي','ی').replace('ك','ک').trim().replace(Regex("\\s+")," "))
            .replace(Regex("""(?<![\p{L}\p{M}\p{N}_])گرم کن(?= (?:(?:(?:سمت|طرف) )?(?:راست|چپ|شاگرد|راننده|همراه|بغل|بغلی|کناری)|رو|را|بذار|بگذار|بزار|تنظیم کن)(?![\p{L}\p{M}\p{N}_]))"""),"گرمکن")
        if(action=="UNKNOWN")return null
        val descentRequest = action == "SET_HILL_DESCENT_CONTROL" &&
            Regex("""(?:ماشین|خودرو)(?: رو| را)? (?:تو|در) سرازیری کنترل کن""").matches(text)
        if (descentRequest && params["enabled"] != true) return "درخواست کنترل خودرو در سرازیری به فعال بودن کنترل سرازیری نیاز دارد"
        val laneRequest = action == "SET_LANE_SUPPORT_MODE" &&
            Regex("""(?:بین|میان) (?:خطوط|خط ها) رانندگی کن""").matches(text)
        if (laneRequest && params["enabled"] != true) return "درخواست رانندگی بین خطوط به فعال بودن کمک بین خطوط نیاز دارد"
        val displayLockRequest = action == "SET_REAR_DISPLAY_LOCK" &&
            Regex("""(?:قفل (?:نمایشگر|مانیتور) عقب(?: رو| را)? (?:باز کن|بردار)|(?:نمایشگر|مانیتور) عقب(?: رو| را)? قفل کن)""").matches(text)
        if (displayLockRequest && params["enabled"] != text.endsWith("قفل کن")) return "وضعیت قفل نمایشگر با درخواست سازگار نیست"
        if (action in explicitToggleActions && "enabled" in params && !displayLockRequest && !laneRequest && !descentRequest && !toggleState.containsMatchIn(text)) return "وضعیت روشن یا خاموش مشخص نشده است"
        if (text == "صدای موزیک رو از میوت دربیار" || text == "صدای موسیقی رو میوت کن") {
            if (action != "SET_AUDIO_MUTE" || params["channel"] != "media" || params["enabled"] != text.endsWith("میوت کن"))
                return "وضعیت قطع یا وصل صدای رسانه با درخواست سازگار نیست"
        }
        if ("AMBIENT_LIGHT" in action && text.split(' ').any { it == "خانه" || it == "خونه" }) return "نور خانه خارج از کابین خودرو است"
        val airflow=RahaWordPiece.airflowMode(text)
        if(airflow!=null && (action!="SET_AIRFLOW_DIRECTION" || params["direction"]!=airflow))return "هدف جمله تنظیم جهت باد است"
        val targets=spans(text,entities).filterNot { airflow!=null && it.kind=="window" }.map { target ->
            if(target.text=="شارژر" && openCharger.matchAt(text,target.start)!=null)target.copy(kind="charge_port") else target
        }
        val ops=spans(text,operations).filter { s->targets.none { s.start<it.end && it.start<s.end } } +
            if (action == "SET_DEFROSTER") spans(text, linkedMapOf("on" to listOf("شروع کن"), "off" to listOf("متوقف کن"))) else emptyList()
        fun reject()="برداشت فرمان با هدف یا عمل گفته‌شده سازگار نیست"
        val rearAddresses=rearAddress.findAll(text).toList()
        for(address in rearAddresses) {
            val onLeft=address.groupValues[1] in setOf("راننده","چپ")
            if("window" in params && params["window"]!=(if(onLeft)"rearLeft" else "rearRight"))return reject()
            if("seat" in params || "zone" in params || "display" in params || action in setOf("SET_MIRROR_FOLD","SET_PASSENGER_DISPLAY_POWER"))return reject()
            if(action=="SET_CHILD_LOCK" && params["side"]!=(if(onLeft)"left" else "right"))return reject()
        }
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
        val sceneClose=action=="DEACTIVATE_SCENE" && "close" in kinds && Regex(left+"(?:سناریو|سناریوی)"+right).containsMatchIn(text)
        if(kinds.any{it in setOf("open","close")} && !displayLockRequest && !sceneClose && !matches(action,listOf("OPEN_","CLOSE_","SET_SUNROOF_POSITION","ADJUST_SUNROOF_POSITION","SET_SUNSHADE_POSITION","ADJUST_SUNSHADE_POSITION","SET_WINDOW_POSITION","ADJUST_WINDOW_POSITION","SET_DOOR_LOCK","SET_MIRROR_FOLD")))return reject()
        if("enabled" in params) {
            if("off" in kinds && params["enabled"]==true || "on" in kinds && params["enabled"]==false)return reject()
            if(kinds.any{it in setOf("heat","cool","massage")} && params["enabled"]==false)return reject()
        }
        val delta=(params["delta"] as? Number)?.toDouble()
        if(action=="ADJUST_CABIN_TEMPERATURE" && delta!=null && ("heat" in kinds && delta<=0 || "cool" in kinds && delta>=0))return reject()
        val percent=(params["percent"] as? Number)?.toDouble()
        val middle=RahaIntentGrounding.isMiddle(text)
        if(middle && action in setOf("CLOSE_SUNROOF","OPEN_SUNROOF","SET_SUNROOF_POSITION") && percent!=50.0)return reject()
        if(percent!=null && !middle && ("close" in kinds && percent!=0.0 || "open" in kinds && percent==0.0))return reject()
        if(targets.any{it.kind in setOf("sunroof","window","sunshade")} && kinds.any{it in setOf("on","off")} && !action.endsWith("DEFROSTER")) {
            if(action!="VENT_WINDOW" || "off" in kinds || !Regex(left+"(?:تهویه|ونت)"+right).containsMatchIn(text))return reject()
            val scope=params["window"]
            if(targets.any{it.kind=="sunroof"} != (scope in setOf("sunroof","allWithSunroof")) ||
                targets.any{it.kind=="window"} != (scope!="sunroof"))return reject()
        }
        val physical=setOf("sunroof","sunshade","window","seat","seat_heat","seat_cool","seat_massage")
        if(physical.any{matches(action,capabilities.getValue(it))}) {
            val marker=Regex(left+"(?:رو|را)"+right).find(text)
            if(marker!=null && text.substring(0,marker.range.first).isNotBlank() && targets.none{it.end<=marker.range.first}) {
                val recipient=text.substring(0,marker.range.first).trim().replaceFirst(Regex("^(?:سمت|طرف) "),"")
                val seatNames=when(params["seat"]) {
                    "driver" -> setOf("راننده","چپ")
                    "passenger" -> setOf("همراه","شاگرد","راست","بغل","بغلی","کناری")
                    else -> emptySet()
                }
                if(targets.none{it.kind=="seat_massage"} || recipient !in seatNames)return reject()
            }
            if(targets.any{it.kind in physical}) {
                val remaining=text.toCharArray()
                for(s in targets+ops)for(i in s.start until s.end)remaining[i]=' '
                if("window" in params)for(address in rearAddresses)for(i in address.range)remaining[i]=' '
                var residual=String(remaining)
                val fillers="رها لطفا لطفاً رو را کن کنید بده بکن بکنید بذار بگذار تنظیم تا روی به برام برای من کمی کم بیشتر کمتر زیاد بالا پایین ببر بیار بکش راننده شاگرد همراه چپ راست جلو عقب پشت عقبی جلویی سمت طرف همه هر دو نصف نصفه نیمه کامل کاملا کاملاً ته حداکثر حداقل درصد درجه سطح پله یکی یه یک دو سه چهار پنج شش شیش هفت هشت نه ده بیست سی چهل پنجاه شصت هفتاد هشتاد نود صد و صفر میشه میتونی می تونی بازش بسته اش آخر لای کناری بغل بغلی ها های تمام بیشترین کمترین ونت حافظه مموری موقعیت ذخیره بازیابی لود شدت وسط متوسط میانه".split(' ').toMutableSet()
                for(p in listOf("از تو می خوام","می خوام","قرار بگیره","باید","باشه","جای بغلی","تو حالت","از کار بنداز"))residual=pattern(p).replace(residual," ")
                if(matches(action,listOf("RECALL_SEAT_POSITION","SAVE_SEAT_POSITION","SET_SEAT_","ADJUST_SEAT_")))fillers.addAll("تو در وضعیت حالت شماره کمک".split(' '))
                residual=residual.replace(Regex("\\s+")," ")
                if(action=="SAVE_SEAT_POSITION")residual=Regex(left+"(?:آپدیت|ب(?:ه ?)?روز ?رسانی) کن"+right).replace(residual," ")
                if(action=="RECALL_SEAT_POSITION")residual=Regex(left+"(?:قرار (?:بده|دهید)|تغییر بده|برگردون)"+right).replace(residual," ")
                if(action=="RECALL_SEAT_POSITION" && params["seat"]=="driver" && params["slot"]=="driver" &&
                    Regex(left+"(?:حالت|موقعیت|وضعیت) رانندگی"+right).containsMatchIn(text))fillers.add("رانندگی")
                if(action.startsWith("ADJUST_")) {
                    fillers.addAll(listOf("یکم","یکمی"))
                    residual=Regex(left+"(?:یه|یک) مقدار"+right).replace(residual," ")
                }
                if(action=="ADJUST_SEAT_VENTILATION")fillers.add("زیادتر")
                if(action=="SET_SEAT_MASSAGE_PATTERN") {
                    fillers.addAll("مد مود مدل الگو الگوی نوع شیوه".split(' '))
                    val aliases = when(params["pattern"]) {
                        "rolling" -> listOf("رولینگ","رول ینگ","rolling","غلتکی","غلتشی","چرخشی")
                        "wave" -> listOf("موجی","موج دار","ویوینگ","وی وینگ","wave","waving")
                        else -> emptyList()
                    }
                    for(alias in aliases) residual=pattern(alias).replace(residual," ")
                }
                if(action in setOf("CLOSE_SUNROOF","OPEN_SUNROOF") && Regex(left+"(?:بسته|باز) (?:بشه|شه|شود|بشود)"+right).containsMatchIn(text))fillers.addAll(listOf("بشه","شه","شود","بشود"))
                if(action=="VENT_WINDOW")fillers.add("حالت")
                if(action in setOf("SET_DEFROSTER","SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"))fillers.addAll("مه بخار گرفته گرفتن داره بره".split(' '))
                if(action in setOf("SET_DEFROSTER","SET_FRONT_DEFROSTER","SET_REAR_DEFROSTER"))for(p in listOf("مه رو بره","مه گرفته","مه داره","مه بره","بخار گرفته","بخار داره","بخار بره"))residual=pattern(p).replace(residual," ")
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
