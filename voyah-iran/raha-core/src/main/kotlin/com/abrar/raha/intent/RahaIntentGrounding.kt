package com.abrar.raha.intent

/** Only explicit quantities constrain model output; directions remain learned. */
object RahaIntentGrounding {
    private val middleValues: Map<String, Pair<String, Any>> = mapOf(
        "SET_AUDIO_VOLUME" to ("level" to 15), "SET_AMBIENT_LIGHT_BRIGHTNESS" to ("percent" to 50),
        "SET_DISPLAY_BRIGHTNESS" to ("percent" to 50), "SET_WINDOW_POSITION" to ("percent" to 50),
        "SET_SUNROOF_POSITION" to ("percent" to 50), "SET_SUNSHADE_POSITION" to ("position" to 50),
        "SET_CABIN_TEMPERATURE" to ("degreesC" to 24), "SET_FAN_SPEED" to ("level" to 4),
        "SET_SEAT_HEATING" to ("level" to 2), "SET_SEAT_VENTILATION" to ("level" to 2),
        "SET_SEAT_MASSAGE" to ("level" to 2), "SET_WIPER_SENSITIVITY" to ("level" to 2),
        "SET_FRAGRANCE_INTENSITY" to ("intensity" to "medium"), "SET_REGENERATION_LEVEL" to ("level" to "medium"),
        "SET_SUSPENSION_HEIGHT" to ("level" to "medium"), "SET_BATTERY_RESERVE_TARGET" to ("percent" to 50))
    private fun isMiddle(text: String): Boolean = has(text,"نصف|نصفه|وسط|متوسط|میانه") &&
        !has(text,"(?:نصف|نصفه)\\s+(?:درجه|ساعت|دقیقه|درصد|پله)") &&
        !Regex("[0-9۰-۹٠-٩]").containsMatchIn(text) &&
        !has(text,"صفر|یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده|بیست|سی|چهل|پنجاه|شصت|هفتاد|هشتاد|نود|صد|فعلی|الان")
    data class Result(val parameters: Map<String, Any>, val grounded: Set<String>, val error: String?)
    private val numericKeys = setOf("degreesC", "percent", "position", "reservePercent", "delta", "deltaPercent", "minutes", "durationMinutes", "level", "intensity", "slot")
    private val units = linkedMapOf("صفر" to 0.0,"یک" to 1.0,"یه" to 1.0,"دو" to 2.0,"سه" to 3.0,"چهار" to 4.0,"پنج" to 5.0,"شش" to 6.0,"هفت" to 7.0,"هشت" to 8.0,"نه" to 9.0,"ده" to 10.0,"یازده" to 11.0,"دوازده" to 12.0,"سیزده" to 13.0,"چهارده" to 14.0,"پانزده" to 15.0,"شانزده" to 16.0,"هفده" to 17.0,"هجده" to 18.0,"نوزده" to 19.0,"بیست" to 20.0,"سی" to 30.0,"چهل" to 40.0,"پنجاه" to 50.0,"شصت" to 60.0,"هفتاد" to 70.0,"هشتاد" to 80.0,"نود" to 90.0,"صد" to 100.0)
    private val words = units + mapOf("یکی" to 1.0,"شیش" to 6.0,"نیم" to .5,"نصف" to .5,"نصفه" to .5)
    private fun has(text: String, pattern: String) = Regex("(?<![\\p{L}\\p{M}\\p{N}_])(?:$pattern)(?![\\p{L}\\p{M}\\p{N}_])").containsMatchIn(text)
    private fun digits(text: String) = text.map { when(it) { in '۰'..'۹' -> '0'+(it-'۰'); in '٠'..'٩' -> '0'+(it-'٠'); '٫' -> '.'; else -> it } }.joinToString("")
    data class Quantity(val value: Double, val unit: String, val word: String)
    private fun quantityText(text: String, action: String): String {
        val quantity=Regex("(?<![\\p{L}\\p{N}_])نه\\s+(?:بیشتر|کمتر)(?![\\p{L}\\p{N}_])").replace(text," ")
        return if(action in setOf("SET_FRAGRANCE_CHANNEL","CONFIGURE_FRAGRANCE","SET_FRAGRANCE_DURATION","SET_FRAGRANCE_INTENSITY","ADJUST_FRAGRANCE_INTENSITY"))
            Regex("(?<![\\p{L}\\p{N}_])صد\\s*توم(?:ن|ان)ی(?![\\p{L}\\p{N}_])").replace(quantity,"نامرایحه")
        else quantity
    }
    fun quantities(text: String): List<Quantity> {
        val tokens=Regex("-?[0-9]+(?:\\.[0-9]+)?|[\\p{L}\\p{M}]+").findAll(digits(text)).map { it.value }.toList()
        val found=mutableListOf<Quantity>();var i=0
        while(i<tokens.size) {
            val word=tokens[i]
            var value=word.toDoubleOrNull() ?: words[word]
            if(value==null || (word=="یه" && tokens.getOrNull(i+1) !in listOf("درجه","پله","تا","دقیقه","ساعت","درصد"))) { i++;continue }
            i++
            while(tokens.getOrNull(i)=="و" && tokens.getOrNull(i+1) in words) {
                val part=words.getValue(tokens[i+1])
                if(!(part==.5 || (value!!>=100 && value%100==0.0 && part>0 && part<100) || (value!!>=20 && value%10==0.0 && part>0 && part<10))) break
                value=value!!+part;i+=2
            }
            found+=Quantity(value!!,tokens.getOrNull(i).orEmpty(),word)
        }
        return found
    }
    fun apply(text: String, action: String, params: Map<String,Any>, allowed: Map<String,List<Any>>): Result {
        val out=params.toMutableMap();val grounded=mutableSetOf<String>()
        fun reject(reason:String)=Result(out,grounded,reason)
        if(action in setOf("SET_LANE_SUPPORT_MODE","SET_DRIVER_MONITORING") && isMiddle(text)) return reject("این حساسیت فقط دو سطح کم و زیاد دارد")
        if(isMiddle(text) && action in middleValues) {
            val (key,value)=middleValues.getValue(action)
            val exact=allowed[key]?.firstOrNull { it==value || (it is Number && value is Number && it.toDouble()==value.toDouble()) }
            if(key !in params || exact==null)return reject("برداشت مدل پارامتر مناسب برای مقدار میانی ندارد")
            out[key]=exact;grounded+=key
            return Result(out,grounded,consistency(text,action,out))
        }
        val keys=params.keys.intersect(numericKeys).filterNot { it=="intensity" && params[it] is String }.toSet()
        val found=quantities(quantityText(text,action))
        if(keys.isNotEmpty() && found.isNotEmpty()) {
            if(has(text,"یا|منفی") || found.size!=1 || keys.size!=1) return reject("عدد یا کاربرد آن در جمله مبهم است")
            val quantity=found.single();var value=quantity.value;val key=keys.single()
            if(value<0) return reject("مقدار را مثبت بگو و جهت تغییر را مشخص کن")
            val temperature=action in setOf("SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE")
            val command=Regex("(?<![\\p{L}\\p{N}_])کم\\s+(?:می\\s*)?شنوم(?![\\p{L}\\p{N}_])").replace(text," ")
            val warmer=temperature && has(command,"گرم[\\s\u200c]*تر(?:ش)?")
            val colder=temperature && has(command,"(?:سرد|خنک)[\\s\u200c]*تر(?:ش)?")
            var down=has(command,"کم(?:\\s*تر)?(?:ش)?|کاهش|پایین(?:\\s*تر)?(?:ش)?|ببند") || colder
            var up=has(command,"زیاد(?:\\s*تر)?(?:ش)?|بیشتر(?:ش)?|افزایش|بالا(?:\\s*تر)?(?:ش)?|اضافه|بلند\\s*تر(?:ش)?|باز") || warmer
            if(action=="ADJUST_AUDIO_VOLUME")down=down || has(text,"(?:آروم|آرام|آهسته)[\\s\u200c]*تر(?:ش)?")
            if(action in setOf("ADJUST_AMBIENT_LIGHT_BRIGHTNESS","ADJUST_DISPLAY_BRIGHTNESS"))down=down || has(text,"ملایم[\\s\u200c]*تر(?:ش)?")
            if(key=="deltaPercent") {
                val opening=has(text,"باز")
                val closing=has(text,"بسته|ببند")
                if(opening && closing)return reject("جهت باز یا بسته شدن مبهم است")
                val signed=(params[key] as? Number)?.toDouble() ?: return reject("جهت تغییر مشخص نیست")
                up=signed>0;down=signed<0
                if(quantity.unit=="پله")value*=10
            }
            if(key in setOf("delta","deltaPercent")) {
                if(up==down) return reject("جهت افزایش یا کاهش مشخص نیست")
                if(down)value = -value
            } else if(warmer || colder || has(text,"کم کن|کمتر کن|زیاد کن|بیشتر کن|افزایش|کاهش")) return reject("برداشت مقدار ثابت با درخواست تغییر نسبی سازگار نیست")
            if(quantity.unit=="ساعت") {
                if(key !in setOf("minutes","durationMinutes")) return reject("واحد زمان با فرمان سازگار نیست")
                value*=60
            }
            // "درجه" also means steps colloquially (volume, fan, etc.).
            if(quantity.unit=="دقیقه" && key !in setOf("minutes","durationMinutes")) return reject("واحد زمان با فرمان سازگار نیست")
            if(quantity.unit=="درصد" && key !in setOf("percent","position","reservePercent","deltaPercent")) return reject("واحد درصد با فرمان سازگار نیست")
            if(quantity.word in setOf("نصف","نصفه") && key in setOf("percent","deltaPercent")) value*=100
            val memorySlot=if(key=="slot" && action in setOf("SAVE_SEAT_POSITION","RECALL_SEAT_POSITION")) {
                when(value) { 1.0 -> "driver"; 2.0 -> "2"; 3.0 -> "3"; else -> null }
            } else null
            val exact=allowed[key]?.firstOrNull { (it is Number && it.toDouble()==value) || (memorySlot!=null && it==memorySlot) }
                ?: return reject("عدد گفته شده خارج از محدوده این فرمان است")
            out[key]=exact;grounded+=key
        }
        return Result(out,grounded,consistency(text,action,out))
    }
    fun consistency(original:String,action:String,params:Map<String,Any>):String? {
        val text=RahaCommandClauses.clean(original)
        if(action in setOf("CALL_CONTACT","UNKNOWN","CONFIRM_CALL","CANCEL_DIALOG"))return null
        if(RahaCommandClauses.blocked(text))return "جمله منفی، شرطی یا پرسشی است"
        if(action=="SET_SUNSHADE_POSITION") {
            // A short noun + state request still specifies an unambiguous endpoint.
            val short=Regex("""(?:رها\s+)?(?:لطفا\s+)?(?:(?:پرده|کاور)(?:\s+(?:سقف|سانروف))?|سایبان|سان\s*شید)(?:\s+(?:رو|را))?\s+(?:(?:کامل|کاملا)\s+)?(باز|بسته)""").matchEntire(text)
            if(short!=null && (params["position"] as? Number)?.toDouble()!=(if(short.groupValues[1]=="باز")100.0 else 0.0))return "میزان باز بودن پرده با حالت خواسته شده سازگار نیست"
        }
        if(action=="SET_MIRROR_FOLD") {
            val opening=has(text,"باز(?:ش)?\\s+(?:کن|بشه|باشه|بمونه)")
            val folding=has(text,"(?:جمع(?:ش)?|تا)\\s+(?:کن|بشه|باشه|بمونه)")
            if(opening && folding)return "جهت آینه متناقض است"
            if(opening && params["folded"]==true || folding && params["folded"]==false)return "وضعیت جمع شدن آینه با درخواست سازگار نیست"
        }
        if("enabled" in params) {
            val noOn=Regex("(?<![\\p{L}\\p{N}_])روشن\\s+(?:نمونه|نماند|نباشه|نباشد)(?![\\p{L}\\p{N}_])")
            val noOff=Regex("(?<![\\p{L}\\p{N}_])خاموش\\s+(?:نمونه|نماند|نباشه|نباشد)(?![\\p{L}\\p{N}_])")
            val positive=noOff.replace(noOn.replace(text," ")," ")
            val off=noOn.containsMatchIn(text) || Regex("خاموش|غیر\\s*فعال").containsMatchIn(positive)
            val on=noOff.containsMatchIn(text) || has(positive,"روشن(?:ش)?|(?<!غیر )فعال(?:ش)?")
            if(off && on || off && params["enabled"]==true || on && params["enabled"]==false)return "برداشت روشن یا خاموش با درخواست سازگار نیست"
        }
        if(action in setOf("SET_LANE_SUPPORT_MODE","SET_DRIVER_MONITORING") && isMiddle(text)) return "این حساسیت فقط دو سطح کم و زیاد دارد"
        if(isMiddle(text) && action in middleValues) {
            val (key,value)=middleValues.getValue(action);val actual=params[key]
            return if(actual==value || (actual is Number && value is Number && actual.toDouble()==value.toDouble())) null else "مقدار برداشت شده با مقدار میانی سازگار نیست"
        }
        val requested=explicitNumbers(quantityText(text,action))
        if(action=="SET_CABIN_TEMPERATURE" && requested.any { it<15.5 || it>30.5 })return "دمای قابل تنظیم از ۱۵٫۵ تا ۳۰٫۵ است"
        if(action=="ADJUST_CABIN_TEMPERATURE") {
            if(requested.any { it<=0 || it>16 })return "مقدار تغییر دما خارج از محدوده است"
            val delta=(params["delta"] as? Number)?.toDouble() ?: 0.0
            if(Regex("کم(?:تر)? کن|کاهش|بیار پایین").containsMatchIn(text) && delta>0 || Regex("زیاد کن|بیشتر کن|افزایش|ببر بالا").containsMatchIn(text) && delta<0)return "جهت تغییر دما سازگار نیست"
        }
        if(requested.isEmpty())return null
        val actual=params.filterKeys { it in numericKeys || it=="channel" }.values.filterIsInstance<Number>().map { kotlin.math.abs(it.toDouble()) }
        if(actual.isEmpty())return null
        val allowed=(if("deltaPercent" in params && has(text,"پله")) requested.map { it*10 } else requested).toMutableSet()
        if("ساعت" in text)allowed.addAll(requested.map { it*60 })
        if(("نصف" in text || "نیمه" in text) && params.keys.any { "percent" in it.lowercase() })allowed+=50.0
        return if(actual.none { it in allowed })"عدد برداشت شده با درخواست سازگار نیست" else null
    }
    private fun explicitNumbers(text:String):List<Double> {
        val tokens=digits(text).split(' ');val values=mutableListOf<Double>();var i=0
        while(i<tokens.size) {
            val word=tokens[i]
            var value=word.toDoubleOrNull() ?: when(word) { "یکی"->1.0;"شیش"->6.0;"نیم","نصف","نصفه","نیمه"->.5;"یه","نه"->null;else->units[word] }
            if(value==null) { i++;continue }
            while(tokens.getOrNull(i+1)=="و" && (tokens.getOrNull(i+2) in units || tokens.getOrNull(i+2)=="نیم")) {
                value=value!!+(if(tokens[i+2]=="نیم").5 else units.getValue(tokens[i+2]));i+=2
            }
            values+=value!!;i++
        }
        return values
    }
}
