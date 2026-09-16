package com.abrar.voicecommands

data class VehicleCommand(val catalogId: String, val action: String, val parameters: Map<String, Any>, val description: String)
sealed interface Recognition {
    data class Matched(val command: VehicleCommand) : Recognition
    data class Unknown(val reason: String) : Recognition
}

object PersianText {
    fun normalize(raw: String): String {
        var s = raw.lowercase().replace('ي','ی').replace('ك','ک').replace('ۀ','ه')
            .replace(Regex("[\\u200c\\u200e\\u200f]"), " ").replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        s = s.map { when(it) { in '۰'..'۹' -> '0'+(it-'۰'); in '٠'..'٩' -> '0'+(it-'٠'); '٫' -> '.'; else -> it } }.joinToString("")
        s = s.replace(Regex("[،,!؛:]+|(?<!\\d)\\.|\\.(?!\\d)"), " ").replace(Regex("\\s+"), " ").trim()
        val numbers = listOf("صفر","یک","دو","سه","چهار","پنج","شش","هفت","هشت","نه","ده","یازده","دوازده","سیزده","چهارده","پانزده","شانزده","هفده","هجده","نوزده","بیست")
        numbers.forEachIndexed { n,w -> if(n != 9) s=s.replace(Regex("(?<!\\S)$w(?!\\S)"),n.toString()) }
        s=s.replace(Regex("(?<!\\S)سی(?!\\S)"),"30")
        s=s.replace(Regex("(?<!\\S)(20|30) و ([1-9])(?!\\S)")) { (it.groupValues[1].toInt()+it.groupValues[2].toInt()).toString() }
        s=s.replace(Regex("(?<!\\S)(\\d+) و نیم(?!\\S)")) { "${it.groupValues[1]}.5" }
        for(n in listOf("پنجره","شیشه","چراغ","صندلی","آینه")) {
            s=s.replace(Regex("(?<!\\S)${n}ها(?!\\S)"),"$n ها")
            s=s.replace(Regex("(?<!\\S)${n}هارو(?!\\S)"),"$n ها رو")
        }
        return s.replace(Regex("(?<!\\S)هارو(?!\\S)"), "ها رو")
    }
}

/** Stateless pure Kotlin parser. Input must be the final transcript, never an ASR partial. */
class PersianCommandRecognizer(private val supported: Set<String> = SupportedCatalog.ids) {
    fun recognize(raw: String): Recognition {
        val text=PersianText.normalize(raw)
        val words=text.split(' ')
        if(text.isBlank() || '?' in text || '؟' in text || words.any { it in blocked }) return Recognition.Unknown("جملهٔ نامفهوم یا پشتیبانی‌نشده")
        val candidates=mutableListOf<VehicleCommand>()
        fun rule(id: String, block: Scan.()->VehicleCommand?) {
            if(id !in supported) return
            val scan=Scan(words); val result=scan.block()
            if(result!=null && scan.finished()) candidates+=result.copy(catalogId=id)
        }
        fun toggle(id:String, action:String, label:String, nouns:String, onlyOn:Boolean=false) {
            rule(id) {
                if(take(nouns)==null) return@rule null
                val enabled=power() ?: return@rule null
                if(onlyOn && !enabled) return@rule null
                cmd(action,"$label ${if(enabled) "روشن" else "خاموش"} می‌شود","enabled" to enabled)
            }
        }
        fun adjust(id:String, action:String, label:String, nouns:String, onlyUp:Boolean=false) {
            rule(id) {
                if(take(nouns)==null) return@rule null
                val direction=direction() ?: return@rule null
                if(onlyUp && direction!="up") return@rule null
                cmd(action,"$label یک پله ${if(direction=="up") "بیشتر" else "کمتر"} می‌شود","direction" to direction)
            }
        }
        adjust("VC003","audio.volume.adjust","صدا","صدا|ولوم|صدای موسیقی|صدای آهنگ")
        adjust("VC004","display.brightness.adjust","روشنایی نمایشگر","روشنایی|روشنایی صفحه|روشنایی نمایشگر|نور صفحه|نور مانیتور")
        rule("VC005") {
            if(take("آهنگ|موسیقی|موزیک|ترک")==null) return@rule null
            val d=when { take("بعدی|بعد")!=null->"next"; take("قبلی|قبل")!=null->"previous"; else->return@rule null }
            cmd("media.track.skip",if(d=="next") "آهنگ بعدی پخش می‌شود" else "آهنگ قبلی پخش می‌شود","direction" to d)
        }
        rule("VC006") {
            val state=when { take("توقف|متوقف|قطع|مکث|استاپ|پاز|نگه دار|pause")!=null->"pause";take("ادامه|از سر بگیر|پلی|play")!=null->"play";else->return@rule null }
            if(take("پخش|موسیقی|موزیک|آهنگ")==null) return@rule null
            take("پخش")
            cmd("media.playback.set",if(state=="play") "پخش ادامه پیدا می‌کند" else "پخش متوقف می‌شود","state" to state)
        }
        rule("VC034") {
            if(take("عقب|صندلی عقب|صندلی های عقب|سرنشینان عقب|سرنشین عقب")==null) return@rule null
            val show=take("نمایش|نشان|نشون|ببینم")!=null; val camera=take("دوربین|پایش")!=null
            val open=position()
            if(open=="closed" || !(show || camera && open=="open")) return@rule null
            cmd("camera.rear_occupants.open","تصویر دوربین پایش صندلی عقب نمایش داده می‌شود")
        }
        for((id,name,aliases) in listOf(Triple("VC035","camping","کمپ|کمپینگ"),Triple("VC036","rest","استراحت|چرت|خواب"))) rule(id) {
            if(take(aliases)==null || !optionalEnable()) return@rule null
            cmd("scene.$name.open","حالت ${if(name=="rest") "استراحت" else "کمپ"} فعال می‌شود")
        }
        rule("VC037") {
            if(take("استراحت|چرت|خواب")==null || number()!=10.0 || take("دقیقه|دقیقه ای")==null || !optionalEnable()) return@rule null
            cmd("scene.rest.duration","زمان استراحت روی ۱۰ دقیقه تنظیم می‌شود","minutes" to 10)
        }
        toggle("VC039","ambient.power.set","نورپردازی داخل","نورپردازی|نورپردازی داخل|نور مخفی|نور محیط",true)
        adjust("VC040","ambient.brightness.adjust","نورپردازی داخل","نورپردازی|روشنایی نورپردازی|نور مخفی|نور محیط",true)
        rule("VC041") {
            if(take("نور|نورپردازی|نور مخفی|چراغ")==null || take("موسیقی|موزیک|آهنگ")==null || take("هماهنگ|همگام|ریتمیک")==null || !optionalEnable()) return@rule null
            cmd("ambient.music_sync.set","نورپردازی با موسیقی هماهنگ می‌شود","enabled" to true)
        }
        toggle("VC042","lighting.rear_fog.set","مه‌شکن عقب","مه شکن عقب|مهشکن عقب|چراغ مه شکن عقب",true)
        toggle("VC043","lighting.automatic.set","چراغ خودکار","چراغ خودکار|چراغ اتومات|چراغ اتوماتیک|چراغ های خودکار|چراغ های اتوماتیک")
        toggle("VC044","lighting.power.set","چراغ‌ها","چراغ|چراغ ها|چراغ های ماشین")
        toggle("VC045","lighting.auto_high_beam.set","نوربالای هوشمند","نور بالای هوشمند|نوربالای هوشمند|نور بالا هوشمند|نوربالای اتوماتیک")
        toggle("VC046","lighting.low_beam.set","نورپایین","نورپایین|نور پایین|چراغ نور پایین")
        for((id,mode,names) in listOf(Triple("VC047","eco","اکو|اقتصادی|اکونومی"),Triple("VC048","comfort","کامفورت|کامفرت|راحت"),Triple("VC049","individual","شخصی|شخصی سازی|شخصی سازی شده|انفرادی"),Triple("VC050","sport","اسپورت|اسپرت|ورزشی"))) rule(id) {
            if(take(names)==null || !optionalEnable()) return@rule null
            take("رانندگی")
            cmd("drive.mode.set","حالت رانندگی روی ${names.substringBefore('|')} تنظیم می‌شود","mode" to mode)
        }
        toggle("VC051","comfort.easy_entry.set","ورود راحت","ورود راحت|ورود آسان|ورود و خروج راحت",true)
        toggle("VC052","climate.power.set","تهویه","تهویه|تهویه هوا|کولر|سیستم تهویه")
        rule("VC053") {
            if(take("تهویه خودکار|کولر اتومات|کولر اتوماتیک|تهویه اتومات|تهویه اتوماتیک")==null || !optionalEnable()) return@rule null
            cmd("climate.auto.set","تهویه خودکار فعال می‌شود","enabled" to true)
        }
        for(relative in listOf(false,true)) rule(if(relative) "VC055" else "VC054") {
            if(take("دما|دمای|دمای کولر|دمای تهویه|درجه کولر|درجه تهویه")==null) return@rule null
            val zone=zone() ?: return@rule null
            val d=direction()
            if(relative && d==null || !relative && d!=null) return@rule null
            val n=number(); take("درجه")
            val value=if(relative) n ?: 0.5 else n ?: return@rule null
            if(value*2%1!=0.0 || (if(relative) value<=0 || value>14 else value<16 || value>30)) return@rule null
            if(relative) cmd("climate.temperature.adjust","دمای ${zoneLabel(zone)} به اندازهٔ $value درجه ${if(d=="up") "بیشتر" else "کمتر"} می‌شود","zone" to zone,"direction" to d!!,"amountCelsius" to value)
            else cmd("climate.temperature.set","دمای ${zoneLabel(zone)} روی $value درجه تنظیم می‌شود","zone" to zone,"celsius" to value)
        }
        adjust("VC056","climate.fan.adjust","سرعت فن","باد|باد کولر|سرعت فن|فن|قدرت باد")
        toggle("VC057","climate.front_defog.set","بخارگیر جلو","بخارگیر جلو|بخار گیر جلو|بخارگیر شیشه جلو")
        toggle("VC058","climate.rear_defrost.set","بخارگیر عقب","بخارگیر عقب|بخار گیر عقب|بخارگیر شیشه عقب|گرمکن شیشه عقب")
        rule("VC059") {
            if(take("گردش هوا|گردش هوای|گردش")==null) return@rule null
            val mode=when { take("داخل|داخلی")!=null->"inside";take("خارج|خارجی|بیرون|بیرونی")!=null->"outside";else->return@rule null }
            if(!optionalEnable()) return@rule null
            cmd("climate.recirculation.set","گردش هوا روی ${if(mode=="inside") "داخل" else "خارج"} تنظیم می‌شود","mode" to mode)
        }
        rule("VC060") {
            if(take("دما|دمای|دمای جلو|دمای 2 طرف")==null || take("یکسان|همسان|هماهنگ|سینک")==null || !optionalEnable()) return@rule null
            cmd("climate.sync.set","دمای دو سمت جلو یکسان می‌شود","enabled" to true)
        }
        toggle("VC061","climate.fragrance.set","خوشبوکننده","خوشبوکننده|خوشبو کننده|عطر")
        for((id,feature,names) in listOf(Triple("VC062","heating","گرمکن|گرم کن|گرمایش"),Triple("VC063","ventilation","تهویه|سردکن|سرد کن|خنک کن"),Triple("VC064","massage","ماساژ|ماساژور"))) rule(id) {
            if(take("صندلی")==null || take(names)==null) return@rule null
            val seat=seat() ?: return@rule null; val enabled=power() ?: return@rule null
            cmd("seat.$feature.set","${names.substringBefore('|')} صندلی ${zoneLabel(seat)} ${if(enabled) "روشن" else "خاموش"} می‌شود","seat" to seat,"enabled" to enabled)
        }
        rule("VC065") {
            if(take("حافظه")==null || take("صندلی")==null) return@rule null
            val value=number() ?: return@rule null
            if(value%1!=0.0 || value !in 1.0..3.0) return@rule null
            take("راننده");take("فراخوانی|برگردون|بازیابی|بیار")
            cmd("seat.memory.recall","حافظهٔ ${value.toInt()} صندلی راننده فراخوانی می‌شود","slot" to value.toInt())
        }
        toggle("VC066","mirror.heating.set","گرمکن آینه‌ها","گرمکن آینه|گرم کن آینه|گرمکن آینه ها|گرم کن آینه ها")
        for(all in listOf(false,true)) rule(if(all) "VC068" else "VC067") {
            if(take("شیشه|پنجره")==null) return@rule null
            val target=window() ?: return@rule null
            if(all!=(target=="all")) return@rule null
            val p=position() ?: return@rule null
            cmd(if(all) "window.all.set" else "window.position.set","${windowLabel(target)} کاملاً ${if(p=="open") "باز" else "بسته"} می‌شود",*if(all) arrayOf("position" to p) else arrayOf("window" to target,"position" to p))
        }
        rule("VC069") {
            if(take("شیشه|پنجره")==null || window()!="all" || take("تهویه")==null) return@rule null
            cmd("window.all.vent","همهٔ شیشه‌ها در وضعیت تهویه قرار می‌گیرند")
        }
        rule("VC070") {
            if(take("پرده سقف|پرده سانروف|پرده ی سقف")==null) return@rule null
            val p=position() ?: return@rule null
            cmd("roof.blind.set","پردهٔ سقف ${if(p=="open") "باز" else "بسته"} می‌شود","position" to p)
        }
        rule("VC071") {
            if(take("صندوق|صندوق عقب|درب صندوق|در صندوق|درب صندوق عقب")==null) return@rule null
            val p=position() ?: return@rule null
            cmd("tailgate.set","درِ صندوق ${if(p=="open") "باز" else "بسته"} می‌شود","position" to p)
        }
        toggle("VC072","door.child_lock.set","قفل کودک","قفل کودک|قفل بچه")
        for((id,name,action) in listOf(Triple("VC073","باک","fuel.flap.open"),Triple("VC074","شارژ","charge.flap.open"))) rule(id) {
            if(take("درب $name|در $name|درپوش $name|دریچه $name")==null || position()!="open") return@rule null
            cmd(action,"درب $name باز می‌شود")
        }
        rule("VC075") {
            if(take("بازیابی|بازیابی انرژی|ریکاوری|احیای انرژی")==null) return@rule null
            val level=when { take("ضعیف|کم")!=null->"weak";take("متوسط|میانه")!=null->"medium";take("قوی|زیاد")!=null->"strong";else->return@rule null }
            if(!optionalEnable()) return@rule null
            cmd("recovery.level.set","بازیابی انرژی روی ${mapOf("weak" to "ضعیف","medium" to "متوسط","strong" to "قوی")[level]} تنظیم می‌شود","level" to level)
        }
        rule("VC076") {
            if(take("برگرد|بازگشت|صفحه قبلی|صفحه قبل|بک|برو عقب")==null) return@rule null
            cmd("ui.back","بازگشت اندروید اجرا می‌شود")
        }
        val unique=candidates.distinctBy { it.action to it.parameters }
        return if(unique.size==1) Recognition.Matched(unique.single()) else Recognition.Unknown(if(unique.isEmpty()) "فرمان پشتیبانی‌شده‌ای تشخیص داده نشد" else "فرمان مبهم است")
    }

    private class Scan(private val words:List<String>) {
        private val used=BooleanArray(words.size)
        fun take(aliases:String):String? {
            for(phrase in aliases.split('|').sortedByDescending { it.length }) {
                val parts=phrase.split(' ')
                for(start in 0..(words.size-parts.size)) {
                    if(parts.indices.all { !used[start+it] && words[start+it]==parts[it] }) {
                        parts.indices.forEach { used[start+it]=true };return phrase
                    }
                }
            }
            return null
        }
        fun number():Double? {
            val i=words.indices.firstOrNull { !used[it] && words[it].matches(Regex("[0-9]+(?:\\.[0-9]+)?")) } ?: return null
            used[i]=true;return words[i].toDoubleOrNull()
        }
        fun power():Boolean?=when {
            take("خاموش|غیرفعال|غیر فعال|خاموش کردن|غیر فعال کردن")!=null->false
            take("روشن|فعال|روشن کردن|فعال کردن|راه بنداز")!=null->true
            else->null
        }
        fun optionalEnable():Boolean { if(power()==false) return false;return position()!="closed" }
        fun position():String?=when { take("باز|باز کردن|بازش|پایین|بده پایین")!=null->"open";take("ببند|بسته|بستن|بالا|بده بالا|ببندش")!=null->"closed";else->null }
        fun direction():String?=when { take("بیشتر|زیاد|بلندتر|بلند|افزایش|بالا|گرمتر|گرم تر")!=null->"up";take("کمتر|کم|آرومتر|آرامتر|کاهش|پایین|سردتر|خنک تر")!=null->"down";else->null }
        fun seat():String?=when { take("راننده|جلو چپ|جلو سمت چپ")!=null->"driver";take("شاگرد|سرنشین جلو|جلو راست|جلو سمت راست")!=null->"passenger";else->null }
        fun zone():String? { val s=seat();val b=take("جلو|2 طرف|هر 2|همه");return if(s!=null && b!=null) null else s ?: "front" }
        fun window():String? {
            val s=when { take("عقب سمت چپ|عقب چپ|پشت راننده")!=null->"rear_left";take("عقب سمت راست|عقب راست|پشت شاگرد")!=null->"rear_right";else->seat() }
            val all=take("همه|تمام|کلیه");return if(s!=null && all!=null) null else s ?: "all"
        }
        fun finished()=words.indices.all { used[it] || words[it] in filler }
    }
    companion object {
        private val filler=setOf("لطفا","خواهشا","رو","را","کن","کنید","کنین","بکن","بکنید","بده","بدید","کردن","بذار","بگذار","بزار","بشه","بشود","قرار","بگیر","ببر","برو","بیار","تنظیم","تغییر","به","روی","برای","سمت","طرف","حالت","مود","ها","های","ی","با","شماره")
        private val blocked=setOf("نه","نکن","نکنید","نکنین","نشه","نبند","نبندید","نباشه","نمیخوام","نمی","نخواهم","اگر","اگه","چرا","آیا","مگر","جز","بجز","بغیر","نیست","بود","بوده","است","هست","بازه")
        private fun cmd(action:String,description:String,vararg args:Pair<String,Any>)=VehicleCommand("",action,mapOf(*args),description)
        private fun zoneLabel(s:String)=when(s){"driver"->"راننده";"passenger"->"شاگرد";else->"دو سمت جلو"}
        private fun windowLabel(s:String)=when(s){"driver"->"شیشهٔ راننده";"passenger"->"شیشهٔ شاگرد";"rear_left"->"شیشهٔ عقب چپ";"rear_right"->"شیشهٔ عقب راست";else->"همهٔ شیشه‌ها"}
    }
}
