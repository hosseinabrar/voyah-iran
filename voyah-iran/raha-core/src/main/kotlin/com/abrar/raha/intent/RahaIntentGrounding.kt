package com.abrar.raha.intent

/** Only explicit quantities constrain model output; directions remain learned. */
object RahaIntentGrounding {
    data class Result(val parameters: Map<String, Any>, val grounded: Set<String>, val error: String?)
    private val numericKeys = setOf("degreesC", "percent", "reservePercent", "delta", "deltaPercent", "minutes", "durationMinutes", "level", "intensity", "slot")
    private val units = linkedMapOf("صفر" to 0.0,"یک" to 1.0,"یه" to 1.0,"دو" to 2.0,"سه" to 3.0,"چهار" to 4.0,"پنج" to 5.0,"شش" to 6.0,"هفت" to 7.0,"هشت" to 8.0,"نه" to 9.0,"ده" to 10.0,"یازده" to 11.0,"دوازده" to 12.0,"سیزده" to 13.0,"چهارده" to 14.0,"پانزده" to 15.0,"شانزده" to 16.0,"هفده" to 17.0,"هجده" to 18.0,"نوزده" to 19.0,"بیست" to 20.0,"سی" to 30.0,"چهل" to 40.0,"پنجاه" to 50.0,"شصت" to 60.0,"هفتاد" to 70.0,"هشتاد" to 80.0,"نود" to 90.0,"صد" to 100.0)
    private val words = units + mapOf("یکی" to 1.0,"شیش" to 6.0,"نیم" to .5,"نصف" to .5,"نصفه" to .5)
    private fun has(text: String, pattern: String) = Regex("(?<![\\p{L}\\p{M}\\p{N}_])(?:$pattern)(?![\\p{L}\\p{M}\\p{N}_])").containsMatchIn(text)
    private fun digits(text: String) = text.map { when(it) { in '۰'..'۹' -> '0'+(it-'۰'); in '٠'..'٩' -> '0'+(it-'٠'); '٫' -> '.'; else -> it } }.joinToString("")
    data class Quantity(val value: Double, val unit: String, val word: String)
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
                if(!(part==.5 || (value!!>=20 && value%10==0.0 && part>0 && part<10))) break
                value=value!!+part;i+=2
            }
            found+=Quantity(value!!,tokens.getOrNull(i).orEmpty(),word)
        }
        return found
    }
    fun apply(text: String, action: String, params: Map<String,Any>, allowed: Map<String,List<Any>>): Result {
        val out=params.toMutableMap();val grounded=mutableSetOf<String>()
        fun reject(reason:String)=Result(out,grounded,reason)
        val keys=params.keys.intersect(numericKeys)
        val found=quantities(text)
        if(keys.isNotEmpty() && found.isNotEmpty()) {
            if(has(text,"یا|منفی") || found.size!=1 || keys.size!=1) return reject("عدد یا کاربرد آن در جمله مبهم است")
            val quantity=found.single();var value=quantity.value;val key=keys.single()
            if(value<0) return reject("مقدار را مثبت بگو و جهت تغییر را مشخص کن")
            val temperature=action in setOf("SET_CABIN_TEMPERATURE","ADJUST_CABIN_TEMPERATURE")
            val warmer=temperature && has(text,"گرم[\\s\u200c]*تر")
            val colder=temperature && has(text,"(?:سرد|خنک)[\\s\u200c]*تر")
            val down=has(text,"کم|کمتر|کاهش|پایین|ببند") || colder
            val up=has(text,"زیاد|بیشتر|افزایش|بالا|باز") || warmer
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
            if(quantity.unit=="درصد" && key !in setOf("percent","reservePercent","deltaPercent")) return reject("واحد درصد با فرمان سازگار نیست")
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
    fun consistency(text:String,action:String,params:Map<String,Any>):String? {
        if(action in setOf("CALL_CONTACT","UNKNOWN","CONFIRM_CALL","CANCEL_DIALOG"))return null
        if("enabled" in params) {
            val off=Regex("خاموش|غیر\\s*فعال").containsMatchIn(text);val on="روشن" in text
            if(off && on || off && params["enabled"]==true || on && params["enabled"]==false)return "برداشت روشن یا خاموش با درخواست سازگار نیست"
        }
        val requested=explicitNumbers(text)
        if(action=="SET_CABIN_TEMPERATURE" && requested.any { it<16 || it>32 })return "دمای قابل تنظیم از ۱۶ تا ۳۲ است"
        if(action=="ADJUST_CABIN_TEMPERATURE") {
            if(requested.any { it<=0 || it>16 })return "مقدار تغییر دما خارج از محدوده است"
            val delta=(params["delta"] as? Number)?.toDouble() ?: 0.0
            if(Regex("کم(?:تر)? کن|کاهش|بیار پایین").containsMatchIn(text) && delta>0 || Regex("زیاد کن|بیشتر کن|افزایش|ببر بالا").containsMatchIn(text) && delta<0)return "جهت تغییر دما سازگار نیست"
        }
        if(requested.isEmpty())return null
        val actual=params.filterKeys { it in numericKeys || it=="channel" }.values.filterIsInstance<Number>().map { kotlin.math.abs(it.toDouble()) }
        if(actual.isEmpty())return null
        val allowed=requested.toMutableSet()
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
