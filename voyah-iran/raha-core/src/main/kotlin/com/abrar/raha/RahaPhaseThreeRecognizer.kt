package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Semantic requests only. Hardware support and execution belong to the vehicle adapter. */
internal object RahaPhaseThreeRecognizer {
    val doors = "درها|در ها|درب ها|دربها|درهای ماشین|در های ماشین|درب های ماشین|در ماشین|درب ماشین|درارو|درا رو|درا|درهارو|درها رو|دربارو|دربا رو|دربا|دررو|درو|دربو|در|درب"
    val windows = "شیشه|شیشه ها|پنجره|پنجره ها"
    val roof = "سانروف|سان روف|سقف شیشه ای"
    val shade = "پرده سقف|پرده سانروف|پرده سان روف|آفتابگیر سقف|آفتاب گیر سقف|پرده|شید|سان شید|سانشید|آفتاب گیر|آفتابگیر|کاور"
    val trunk = "صندوق عقب|صندوق|در صندوق|درب صندوق"
    val charge = "شارژر|درگاه شارژ|سوکت شارژ|درپوش شارژر|پورت شارژر|در شارژر|درب شارژر|درپوش شارژ|دریچه شارژ|در شارژ|درب شارژ|پورت شارژ|در باک شارژ|درب باک شارژ|در باک برق|درب باک برق|مخزن شارژ|مخزن برق"
    val fuel = "درپوش سوخت|دریچه سوخت|در باک|درب باک|باک بنزین|در بنزین|مخزن بنزین|مخزن سوخت"
    val opening = "باز|باز کن|باز کردن|پایین|بده پایین|بیار پایین|بکش پایین"
    val closing = "بسته|ببند|بستن|بالا|بده بالا|ببر بالا"
    val vent = "حالت تهویه|وضعیت تهویه|تهویه|ونت|vent|لای|لای شیشه"
    val small = "یکم|یه کم|1 کم|کمی|کم ای|کم یه|یه مقدار|1 مقدار|یک مقدار|مقداری|کمه|اندکی|کم"
    val full = "تا انتها|تا آخر|تا ته|کاملا|کامل|تمام"
    private const val half = "تا نصفه|تا نصف|تا نیمه|تا نیم|نصفه|نصف|نیمه|نیم"
    val vocabulary = listOf(doors, "قفل|آنلاک|باز", windows, roof, shade, trunk, charge, fuel, opening, closing, vent, small, full, half, "بیشتر|تا نصف|تا نیمه|ببر",
        "قفل کودک|قفل بچه|چپ|راست|راننده|شاگرد|همراه|جلو|عقب|پشت|همه|هر دو|چهارتا|هر چهار|نصف|نیمه|درصد|کامل|تمام|تا آخر|صد|پنجاه|هفتاد|هشتاد|نود")

    fun recognize(text: String): Recognition.Matched? {
        val results = mutableListOf<VehicleCommand>()
        fun rule(body: RahaCommandRecognizer.Scan.() -> Pair<RahaAction, Map<String, Any>>?) {
            var local=text
            for((word,n) in mapOf("صد" to "100","نود" to "90","هشتاد" to "80","هفتاد" to "70","شصت" to "60","پنجاه" to "50","چهل" to "40")) {
                local=local.split(' ').joinToString(" ") { if(it==word) n else it }
            }
            val s=RahaCommandRecognizer.Scan(local)
            val value=s.body() ?: return
            if(s.finished()) results += VehicleCommand(value.first.name, value.first.name, value.second,
                describe(value.first, value.second))
        }
        rule {
            if(!take(doors)) return@rule null
            // Door locks share one central actuator, regardless of spoken seating position.
            while(take("راننده|شاگرد|همراه|سرنشین|مسافر|چپ|راست|جلو|عقب|پشت|ردیف اول|ردیف دوم|ردیف 1|ردیف 2|همه|همگی|تمام|هر دو|هر 2|هر چهار|هر 4|ماشین|خودرو")) { }
            val unlocked=take("باز|آنلاک")
            val lockWord=take("قفل")
            if(!unlocked && !lockWord) return@rule null
            // Central locking only; no inference about opening a physical door.
            RahaAction.SET_DOOR_LOCK to mapOf("door" to "all", "locked" to !unlocked)
        }
        rule {
            // Bare Vent names the all-window preset; plain "ventilation" still means HVAC.
            if(!take("ونت|vent")) return@rule null
            if(power()==false) return@rule null
            take(opening)
            RahaAction.VENT_WINDOW to mapOf("window" to "all")
        }
        rule {
            if(!take(windows)) return@rule null
            val plural=last.endsWith("ها")
            val target=windowTarget(if(plural) "all" else "driver") ?: return@rule null
            if(take(vent)) {
                if(power()==false) return@rule null
                take(opening)
                return@rule RahaAction.VENT_WINDOW to mapOf("window" to target)
            }
            val motion=motion(opening,closing, allowWindowAsrClose=true) ?: return@rule null
            val params=mapOf<String,Any>("window" to target)
            when {
                motion.delta != null -> RahaAction.ADJUST_WINDOW_POSITION to (params + ("deltaPercent" to motion.delta))
                motion.explicit -> RahaAction.SET_WINDOW_POSITION to (params + ("percent" to motion.percent))
                else -> (if(motion.percent==100) RahaAction.OPEN_WINDOW else RahaAction.CLOSE_WINDOW) to params
            }
        }
        rule {
            if(!take("قفل کودک|قفل بچه")) return@rule null
            val left=take("چپ|راننده");val right=take("راست|شاگرد")
            take("عقب|پشت")
            val all=take("همه|هر دو|هر 2|دو طرف|2 طرف")
            if(all && (left || right)) return@rule null
            val enabled=power() ?: true
            RahaAction.SET_CHILD_LOCK to mapOf("side" to if(left && !right) "left" else if(right && !left) "right" else "both", "enabled" to enabled)
        }
        for((noun,openAction,closeAction) in listOf(
            Triple(trunk,RahaAction.OPEN_TAILGATE,RahaAction.CLOSE_TAILGATE),
            Triple(charge,RahaAction.OPEN_CHARGE_PORT,null),
            Triple(fuel,RahaAction.OPEN_FUEL_FLAP,null)
        )) rule {
            if(!take(noun)) return@rule null
            val open=take("باز|باز کردن")
            val close=take("ببند|بسته|بستن")
            if(open == close) return@rule null
            (if(open) openAction else closeAction ?: return@rule null) to emptyMap()
        }
        for(isShade in listOf(false,true)) rule {
            if(!take(if(isShade) shade else roof)) return@rule null
            val motion=motion("باز|باز کردن|کنار بزن", "ببند|بسته|بستن") ?: return@rule null
            when {
                motion.delta != null -> (if(isShade) RahaAction.ADJUST_SUNSHADE_POSITION else RahaAction.ADJUST_SUNROOF_POSITION) to mapOf("deltaPercent" to motion.delta)
                isShade -> RahaAction.SET_SUNSHADE_POSITION to mapOf("position" to motion.percent)
                motion.explicit -> RahaAction.SET_SUNROOF_POSITION to mapOf("percent" to motion.percent)
                else -> (if(motion.percent==100) RahaAction.OPEN_SUNROOF else RahaAction.CLOSE_SUNROOF) to emptyMap()
            }
        }
        return results.distinctBy { it.action to it.parameters }.singleOrNull()?.let { Recognition.Matched(it) }
    }

    private data class Motion(val percent:Int=0,val delta:Int?=null,val explicit:Boolean=false)
    // Positions are absolute openness; deltas are percentage points from current position.
    // The execution adapter must read current position and clamp the result to 0..100.
    private fun RahaCommandRecognizer.Scan.motion(openWords:String,closeWords:String,allowWindowAsrClose:Boolean=false):Motion? {
        val smallStep=take(small)
        if(smallStep) take("دیگه|دیگر")
        val half=take(RahaPhaseThreeRecognizer.half)
        val isFull=take(full)
        val more=take("بیشتر")
        val percentUnit=take("درصد")
        val n=number()
        val open=take(openWords)
        val close=take(closeWords) || (allowWindowAsrClose && !open && take("ببر"))
        if(open && close) return null
        if(smallStep || (more && !half && !isFull && n==null && !percentUnit)) {
            if(half || isFull || n!=null || percentUnit || open==close) return null
            return Motion(delta=if(open) 10 else -10)
        }
        if(half) {
            if(isFull || n!=null || percentUnit) return null
            return Motion(percent=50,explicit=true)
        }
        if(n!=null || percentUnit) {
            if(n==null || !percentUnit || n !in 0.0..100.0 || n%1!=0.0) return null
            val value=if(close) 100-n.toInt() else n.toInt()
            if(isFull && value !in listOf(0,100)) return null
            return Motion(percent=value,explicit=true)
        }
        if(!open && !close) return null
        return Motion(percent=if(open)100 else 0)
    }

    private fun RahaCommandRecognizer.Scan.windowTarget(default: String): String? {
        val rear=take("عقب|پشت")
        val front=take("جلو")
        val left=take("راننده|چپ")
        val right=take("شاگرد|همراه|راست|بغل")
        val all=take("همه|همگی|هر چهار|هر 4|چهارتا|4 تا|تمام")
        if(rear && front || all && (rear || front || left || right)) return null
        return when {
            all -> "all"
            rear && left && !right -> "rearLeft"
            rear && right && !left -> "rearRight"
            rear -> "rear"
            front && !left && !right -> "front"
            left && right -> if(front) "front" else "all"
            left -> "driver"
            right -> "passenger"
            else -> default
        }
    }

    private fun describe(action:RahaAction,p:Map<String,Any>):String {
        val names=mapOf(
            RahaAction.SET_DOOR_LOCK to "قفل درها",
            RahaAction.SET_WINDOW_POSITION to "میزان باز بودن شیشه", RahaAction.ADJUST_WINDOW_POSITION to "تغییر باز بودن شیشه",
            RahaAction.ADJUST_SUNROOF_POSITION to "تغییر باز بودن سانروف", RahaAction.ADJUST_SUNSHADE_POSITION to "تغییر باز بودن پرده",
            RahaAction.OPEN_WINDOW to "باز کردن شیشه",RahaAction.CLOSE_WINDOW to "بستن شیشه",
            RahaAction.VENT_WINDOW to "حالت تهویه شیشه",RahaAction.SET_CHILD_LOCK to "قفل کودک",
            RahaAction.OPEN_TAILGATE to "باز کردن صندوق",RahaAction.CLOSE_TAILGATE to "بستن صندوق",
            RahaAction.OPEN_CHARGE_PORT to "باز کردن درپوش شارژ",RahaAction.OPEN_FUEL_FLAP to "باز کردن درپوش سوخت",
            RahaAction.OPEN_SUNROOF to "باز کردن سانروف",RahaAction.CLOSE_SUNROOF to "بستن سانروف",
            RahaAction.SET_SUNROOF_POSITION to "میزان باز بودن سانروف",RahaAction.SET_SUNSHADE_POSITION to "میزان باز بودن پرده سقف")
        val labels=mapOf("driver" to "راننده", "passenger" to "شاگرد","rearLeft" to "عقب چپ","rearRight" to "عقب راست",
            "all" to "همه", "rear" to "عقب", "front" to "جلو", "both" to "دو طرف", "left" to "چپ", "right" to "راست")
        return names[action] + p.map { (k,v)-> when(k) {
            "locked" -> if(v==true) "قفل کردن" else "باز کردن قفل"
            "enabled" -> if(v==true) "فعال" else "غیرفعال"
            "deltaPercent" -> if((v as Int)>0) "۱۰ درصد بازتر از حالت فعلی" else "۱۰ درصد بسته‌تر از حالت فعلی"
            "percent","position" -> "$v درصد"
            else -> labels[v] ?: v.toString()
        }}.joinToString("، ",prefix=if(p.isEmpty()) "" else ": ")
    }
}
