package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Intent extraction only. Firmware capability checks belong to the vehicle adapter. */
internal object RahaPhaseSixRecognizer {
    val strategies = linkedMapOf(
        "electric" to "اولویت برق|اولویت برقی|اولویت باتری|اول از باتری|اول باتری|اول از برق|اول برق|برق اول|فقط برق|فقط باتری|ای وی فرست|ای وی|تمام الکتریکی|تمام برقی|برقی|الکتریکی|الکتریک",
        "fuel" to "اولویت سوخت|اولویت بنزین|اول از بنزین|اول بنزین|بنزین اول|فقط بنزین|فیول فرست|فیول|بنزینی|سوختی",
        "hybrid" to "هیبرید|هیبریدی|هایبرید|ترکیبی",
        "smart" to "اسمارت|هوشمند",
        "save" to "سیو باتری|سیو|حفظ شارژ|ذخیره شارژ|ذخیره باتری|ذخیره انرژی|ذخیره"
    )
    val scenes = linkedMapOf(
        "camp" to "کمپ|کمپینگ|اردو|اردو زدن",
        "nap" to "استراحت|خواب|چرت|چرت زدن",
        "party" to "پارتی|مهمانی|جشن",
        "babyCare" to "مراقبت از کودک|مراقبت کودک|مراقبت از بچه|مراقبت بچه|بیبی کر",
        "carWash" to "کارواش|شستشوی ماشین|شستن ماشین|شستشو|شست و شوی ماشین|شست و شو|شست وشو",
        "quickWarm" to "گرم شدن سریع|گرمایش سریع|گرم کردن سریع",
        "powerKeep" to "حفظ برق|نگه داشتن برق|روشن ماندن برق|برق در حالت توقف|پاور هولد"
    )
    private const val battery = "کنترل دمای باتری|مدیریت دمای باتری|آماده سازی باتری|پیش گرمایش باتری|پیش گرم کردن باتری|پیش گرمکن باتری|پری هیت باتری"
    val vocabulary = strategies.values + scenes.values + listOf(battery, "باتری ها|باتریها|گرمایش باتری|باتری ها رو گرم کن|دمای باتری رو کنترل کن",
        "مصرف انرژی|انرژی|شارژ|شارژ کردن|متوقف|توقف|قطع|تمام|حفظ|حد|حداقل|درصد|هدف|ذخیره|باتری|چهل|پنجاه|شصت|هفتاد|هشتاد|نود|صد|بیست و پنج|چهل و پنج|پنجاه و پنج|شصت و پنج|هفتاد و پنج",
        "سناریو|سناریوی|حالت|از|خروج|خارج|لغو|پایان|فعلی|فعال|مدت|زمان|دقیقه|ساعت|نیم ساعت|نامحدود|بدون محدودیت|نود|صد و بیست|صد و پنجاه|صد و هشتاد|موتور|بنزین|با موتور|شارژ با موتور|اجازه",
        "آینه|آینه ها|آیینه|آیینه ها|جمع|تا|باز|ببند|راننده|شاگرد|همراه|چپ|راست|همه",
        "بسوزون|بسوزان|مصرف کن|استفاده کن|مصرف سوخت|شارژر|آزاد|دمای باتری|بالا ببر|فعال نگه دار|روشن نگه دار|روشن بمونه|روشن بماند|فعال بماند|نگه دارید|نگهدار|بمونه|بماند|به میزان")

    private fun RahaCommandRecognizer.Scan.duration(): Int? {
        take("زمان|مدت")
        val unlimited=take("نامحدود|بدون محدودیت|همیشه")
        val half=take("نیم ساعت")
        val hours=take("ساعت"); take("دقیقه")
        val n=number()
        val minutes=when {
            unlimited && n==null -> -1
            half && n==null -> 30
            n!=null -> (n*if(hours)60 else 1).takeIf { it%1==0.0 }?.toInt()
            else -> null
        }
        return minutes?.takeIf { it in setOf(-1,30,60,90,120,150,180) }
    }

    fun recognize(text: String): Recognition.Matched? {
        var local = text.replace("بازکردن", "باز کردن")
        // The common normalizer has already translated units (e.g. پنج -> 5).
        for ((word, n) in linkedMapOf("صد" to 100, "نود" to 90, "هشتاد" to 80, "هفتاد" to 70, "شصت" to 60, "پنجاه" to 50, "چهل" to 40))
            local = local.replace(Regex("(?<!\\S)$word(?!\\S)"), n.toString())
        local = local.replace(Regex("(?<!\\S)(100|[3-9]0) و ([1-9]|[2-9]0)(?!\\S)")) {
            (it.groupValues[1].toInt() + it.groupValues[2].toInt()).toString()
        }
        val found = mutableListOf<VehicleCommand>()
        fun rule(action: RahaAction, body: RahaCommandRecognizer.Scan.() -> Map<String, Any>?) {
            val s = RahaCommandRecognizer.Scan(local)
            val p = s.body() ?: return
            while (s.take("ماشین|خودرو|مصرف انرژی|مصرف سوخت|انرژی|سناریوی|سناریو")) { }
            if (s.finished()) found += VehicleCommand(action.name, action.name, p, describe(action, p))
        }
        // Longer strategy aliases win before the unqualified electric target.
        rule(RahaAction.SET_ENERGY_STRATEGY) {
            val matches = strategies.filterValues { aliases -> take(aliases.split('|').joinToString("|") { RahaCommandRecognizer.normalize(it) }) }.keys
            if (matches.size != 1) return@rule null
            val name=last
            val enabled=power()
            if (enabled == false) return@rule null
            take("مصرف سوخت|مصرف انرژی")
            take("مصرف|استفاده|بسوزون|بسوزان|بسوزانید|بسوزونید")
            val strategy=matches.single()
            // Explicit Save + target is one compound intent, validated before either write.
            val n=number()
            if(n!=null) {
                if(strategy!="save" || n !in 25.0..80.0 || n%5!=0.0)return@rule null
                if(name in setOf("حفظ شارژ","ذخیره شارژ") && enabled!=true)return@rule null
                take("درصد");take("به میزان|میزان")
                mapOf("strategy" to strategy,"reservePercent" to n.toInt())
            } else mapOf("strategy" to strategy)
        }
        rule(RahaAction.SET_BATTERY_RESERVE_TARGET) {
            if (!take("حد حفظ شارژ|حد ذخیره شارژ|حفظ شارژ|ذخیره شارژ|شارژ ذخیره|حد شارژ|هدف شارژ|شارژ باتری")) return@rule null
            take("باتری"); take("درصد"); take("حداقل|حد|هدف")
            val n = number() ?: return@rule null
            if (n !in 25.0..80.0 || n % 5 != 0.0) return@rule null
            mapOf("percent" to n.toInt())
        }
        rule(RahaAction.STOP_CHARGING) {
            if (!take("شارژ کردن|شارژ باتری|شارژر|شارژ")) return@rule null
            if (power() != false && !take("تمام|تموم|پایان|لغو|آزاد")) return@rule null
            mapOf()
        }
        rule(RahaAction.SET_BATTERY_PRECONDITIONING) {
            if (!take(battery)) {
                if(!take("باتری ها|باتریها|باتری"))return@rule null
                take("ها")
                val thermal=take("پیش گرمایش|پیش گرمکن|گرمایش|گرمکن|گرم|دمای|دما")
                if(!thermal)return@rule null
                take("کنترل|مدیریت")
                take("بالا|زیاد|افزایش")
            }
            take("ها")
            mapOf("enabled" to (power() ?: true))
        }
        for (activate in listOf(true, false)) rule(if (activate) RahaAction.ACTIVATE_SCENE else RahaAction.DEACTIVATE_SCENE) {
            val selected = scenes.filterValues { aliases -> take(aliases.split('|').joinToString("|") { RahaCommandRecognizer.normalize(it) }) }.keys
            val generic = take("سناریوی فعال|سناریوی فعلی|حالت فعلی|سناریو فعال|سناریو فعلی|سناریو|سناریوی")
            if (selected.size > 1 || (selected.isEmpty() && !generic)) return@rule null
            val exit = take("خارج شو|خارج شوید|خارج شدن|خروج|خارج|بستن|لغو|پایان|تمام|تموم")
            val enabled = power()
            val wantsExit = exit || enabled == false
            if (activate == wantsExit || (activate && selected.isEmpty())) return@rule null
            take("از")
            mapOf("scene" to if(activate)selected.single() else "current")
        }
        rule(RahaAction.SET_POWER_KEEP_DURATION) {
            if (!take(scenes.getValue("powerKeep"))) return@rule null
            val minutes = duration() ?: return@rule null
            mapOf("minutes" to minutes)
        }
        rule(RahaAction.ACTIVATE_SCENE) {
            if(!take("ماشین|خودرو|باتری|برق ماشین"))return@rule null
            val remain=take("بمونه|بماند|بموند|نگه دار|نگه دارید|نگهدار|نگهدارید")
            if(!remain || !take("روشن|فعال"))return@rule null
            val timed=Regex("(?:[0-9]|دقیقه|ساعت|نامحدود)").containsMatchIn(local)
            val minutes=duration()
            if(timed && minutes==null)return@rule null
            mapOf<String,Any>("scene" to "powerKeep") + if(minutes!=null)mapOf("minutes" to minutes) else emptyMap()
        }
        rule(RahaAction.SET_POWER_KEEP_ENGINE_CHARGING) {
            if (!take(scenes.getValue("powerKeep"))) return@rule null
            if (!take("شارژ با موتور|شارژ توسط موتور|شارژ با بنزین")) return@rule null
            take("باتری"); take("اجازه")
            mapOf("enabled" to (power() ?: true))
        }
        rule(RahaAction.SET_MIRROR_FOLD) {
            if (!take("آینه ها|آیینه ها|آینه|آیینه")) return@rule null
            val side = when { take("راننده|چپ") -> "driver"; take("شاگرد|همراه|راست") -> "passenger"; else -> "all" }
            take("همه")
            val folded = take("جمع|تا|ببند|بسته")
            val unfolded = take("باز")
            if (folded == unfolded) return@rule null
            mapOf("side" to side, "folded" to folded)
        }
        return found.distinctBy { it.action to it.parameters }.singleOrNull()?.let { Recognition.Matched(it) }
    }

    private fun describe(action: RahaAction, p: Map<String, Any>): String {
        val names = mapOf(RahaAction.SET_ENERGY_STRATEGY to "حالت مصرف انرژی", RahaAction.SET_BATTERY_RESERVE_TARGET to "حد حفظ شارژ",
            RahaAction.STOP_CHARGING to "توقف شارژ", RahaAction.SET_BATTERY_PRECONDITIONING to "کنترل دمای باتری",
            RahaAction.ACTIVATE_SCENE to "فعال‌سازی سناریو", RahaAction.DEACTIVATE_SCENE to "خروج از سناریو",
            RahaAction.SET_POWER_KEEP_DURATION to "زمان حفظ برق", RahaAction.SET_POWER_KEEP_ENGINE_CHARGING to "شارژ با موتور در حالت حفظ برق",
            RahaAction.SET_MIRROR_FOLD to "جمع یا باز کردن آینه‌ها")
        val labels = (strategies + scenes).mapValues { it.value.substringBefore('|') } + mapOf("current" to "فعلی", "all" to "همه", "driver" to "راننده", "passenger" to "شاگرد")
        return names.getValue(action) + p.entries.joinToString("، ", prefix = if (p.isEmpty()) "" else ": ") { (k, v) ->
            when (k) { "enabled" -> if (v == true) "فعال" else "غیرفعال"; "folded" -> if (v == true) "جمع" else "باز"
                "percent" -> "$v درصد"; "minutes" -> if (v == -1) "نامحدود" else "$v دقیقه"; else -> labels[v] ?: v.toString() }
        }
    }
}
