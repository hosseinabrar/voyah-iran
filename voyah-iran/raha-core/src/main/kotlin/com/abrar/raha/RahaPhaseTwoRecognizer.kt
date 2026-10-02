package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Phase-two intents only. No vehicle access; memory always names the Driver profile. */
internal object RahaPhaseTwoRecognizer {
    val fragranceNames = listOf("خوشبوکننده", "خوشبو کننده", "خوش بوکننده", "خوش بو کننده", "عطر", "رایحه", "اسانس", "عطرپاش", "عطر پاش", "بوگیر", "بو گیر", "بو", "بوی")
    val odorTargets = listOf("هوا", "هوای", "ماشین", "کابین", "اتاق", "داخل", "توی", "تو", "اینجا")
    val odorExamples = listOf(
        "هوا بو گرفته", "بوی بدی میاد", "بو میاد", "ماشین بو گرفته",
        "داخل ماشین بوی بد میاد", "کابین بدبو شده", "بوی نامطبوع میاد",
        "یه بوی بدی میاد", "اینجا بوی بد میده", "هوا بدبو شده",
        "ماشین رو خوشبو کن", "هوا رو خوش بو کن", "یه کم عطر بزن",
        "بوی بد رو از بین ببر", "بوی بد رو برطرف کن"
    )
    val saveWords = listOf("ذخیره", "سیو", "ثبت", "آپدیت", "به روز رسانی", "بروزرسانی", "به روز", "نگه دار")
    val recallWords = listOf("بازیابی", "ریکال", "فراخوانی", "برگردون", "برگردان", "بازگردان", "برگرده", "اعمال", "لود")
    val memoryWords = listOf("مموری", "حافظه", "صندلی", "صندلیم", "موقعیت", "درایور", "driver")
    private val aliases = fragranceNames.joinToString("|")

    fun memoryRestriction(text: String): String? {
        val memory = RahaCommandRecognizer.Scan(text)
        if (!memory.take(memoryWords.joinToString("|"))) return null
        if (!text.contains("مموری") && !text.contains("حافظه") &&
            !memory.take((saveWords + recallWords).joinToString("|"))) return null
        val restrictions = RahaCommandRecognizer.Scan(text)
        if (restrictions.take("شاگرد|همراه|راست|بغل|سرنشین|عقب|پشت|ردیف دوم|هر دو|همه|مهمان|استراحت|ریلکس|خواب|مسافر|پذیرایی"))
            return "مموری فقط برای صندلی راننده و حالت Driver پشتیبانی می‌شود"
        if (restrictions.number() != null || Regex("(?:^| )\\d+(?: |$)").containsMatchIn(text))
            return "فقط پروفایل Driver پشتیبانی می‌شود؛ شمارهٔ حافظه انتخاب نکنید"
        return null
    }

    fun recognize(text: String): Recognition.Matched? {
        odorRequest(text)?.let { return it }
        fragranceConfiguration(text)?.let { return it }
        val matches = mutableListOf<VehicleCommand>()
        fun rule(action: RahaAction, body: RahaCommandRecognizer.Scan.() -> Map<String, Any>?) {
            val scan = RahaCommandRecognizer.Scan(text)
            val values = scan.body()
            if (values != null && scan.finished()) matches += VehicleCommand(action.name, action.name, values, describe(action, values))
        }
        rule(RahaAction.SET_FRAGRANCE_POWER) {
            if (!take(aliases)) return@rule null
            val enabled = power() ?: return@rule null
            mapOf("enabled" to enabled)
        }
        for (scent in RahaFragranceVocabulary.scents) rule(RahaAction.SET_FRAGRANCE_CHANNEL) {
            if (!take(scent.aliases.joinToString("|"))) return@rule null
            take(aliases)
            take("نوع|گزینه|کانال")
            mapOf("channel" to scent.id)
        }
        rule(RahaAction.SET_FRAGRANCE_CHANNEL) {
            if (!take(aliases)) return@rule null
            take("شماره|کانال|نوع|گزینه")
            val channel = number() ?: return@rule null
            if (channel < 1 || channel > Int.MAX_VALUE || channel % 1 != 0.0) return@rule null
            mapOf("channel" to channel.toInt())
        }
        rule(RahaAction.SET_FRAGRANCE_INTENSITY) {
            if (!take(aliases)) return@rule null
            val unit = take("شدت|میزان|غلظت|قدرت|سطح|لول")
            val bound = levelBound()
            val named = when {
                take("متوسط|میانه|مدیوم") -> "medium"
                take("قوی|شدید|بالا") -> "high"
                take("ضعیف|ملایم|پایین") -> "low"
                else -> null
            }
            val number = number()
            val intensity = when {
                bound == "max" -> "high"
                bound == "min" -> "low"
                named != null -> named
                unit && number == 1.0 -> "low"
                unit && number == 2.0 -> "medium"
                unit && number == 3.0 -> "high"
                else -> return@rule null
            }
            if ((bound != null || named != null) && number != null) return@rule null
            levelUnits()
            mapOf("intensity" to intensity)
        }
        rule(RahaAction.ADJUST_FRAGRANCE_INTENSITY) {
            if (!take(aliases)) return@rule null
            take("شدت|میزان|غلظت|قدرت|سطح|لول")
            val delta = direction() ?: return@rule null
            val amount = number() ?: 1.0
            if (amount !in 1.0..2.0 || amount % 1 != 0.0) return@rule null
            levelUnits()
            mapOf("delta" to amount.toInt() * delta)
        }
        for (save in listOf(true, false)) rule(if(save) RahaAction.SAVE_SEAT_POSITION else RahaAction.RECALL_SEAT_POSITION) {
            if (!take(memoryWords.joinToString("|"))) return@rule null
            while(take("مموری|حافظه|موقعیت|صندلی|صندلیم|تنظیمات|تنظیم|حالت|پروفایل")) { }
            if (!take((if(save) saveWords else recallWords).joinToString("|"))) return@rule null
            while(take("راننده|چپ|درایور|درایوینگ|رانندگی|driver|driving|فعلی|الان|همین|قبلی|ذخیره شده")) { }
            mapOf("seat" to "driver", "slot" to "driver")
        }
        return matches.distinctBy { it.action to it.parameters }.singleOrNull()?.let { Recognition.Matched(it) }
    }

    private fun odorRequest(text: String): Recognition.Matched? {
        val scan = RahaCommandRecognizer.Scan(text)
        val wish = scan.take("خوشبو|خوش بو")
        val odor = scan.take("بوی|بو|بدبو|بد بو")
        if (!wish && !odor) return null
        val bad = scan.take("بدی|بد|نامطبوع|ناخوشایند")
        val state = scan.take("گرفته|میاد|می آید|میده|می ده|می دهد|شده|دهد")
        val remove = scan.take("از بین ببر|برطرف|حذف|رفع")
        if (!wish && !state && !(bad && remove)) return null
        while(scan.take(odorTargets.joinToString("|"))) { }
        scan.take("1") // normalized colloquial «یه بوی بدی»
        if (!scan.finished()) return null
        val action = RahaAction.SET_FRAGRANCE_POWER
        return Recognition.Matched(VehicleCommand(
            action.name, action.name, mapOf("enabled" to true),
            "روشن کردن خوشبوکننده با رایحه و تنظیمات فعلی"
        ))
    }

    /**
     * One semantic request, with optional slots. Omitted slots must stay unchanged.
     * Duration 0 means unlimited; channel numbers follow the confirmed vehicle menu.
     */
    private fun fragranceConfiguration(text: String): Recognition.Matched? {
        val scan = RahaCommandRecognizer.Scan(text)
        val noun = scan.take(aliases)
        if (noun) scan.take("1 کم")
        val scent = RahaFragranceVocabulary.scents.firstOrNull { scan.take(it.aliases.joinToString("|")) }
        if (!noun && scent == null) return null
        val values = linkedMapOf<String, Any>()
        if (scent != null) values["channel"] = RahaFragranceVocabulary.scents.indexOf(scent) + 1
        val duration = when {
            scan.take("نامحدود|بدون محدودیت|بدون محدودیت زمانی|همیشه|دائمی|دایمی") -> 0
            scan.take("نیم ساعت|نیم ساعته|30 دقیقه|30 دقیقه ای") -> 30
            scan.take("1 ساعت|یک ساعت|یه ساعت|1 ساعته|ساعتی|60 دقیقه|60 دقیقه ای|شصت دقیقه") -> 60
            else -> null
        }
        if (duration != null) {
            values["durationMinutes"] = duration
            scan.take("مدت|زمان")
        }
        val unit = scan.take("شدت|میزان|غلظت|قدرت|سطح|لول")
        val bound = scan.levelBound()
        val intensity = when {
            scan.take("متوسط|میانه|مدیوم") -> "medium"
            scan.take("قوی|شدید|بالا") -> "high"
            scan.take("ضعیف|ملایم|پایین") -> "low"
            bound == "max" -> "high"
            bound == "min" -> "low"
            unit -> when(scan.number()) { 1.0 -> "low"; 2.0 -> "medium"; 3.0 -> "high"; else -> return null }
            else -> null
        }
        if (intensity != null) values["intensity"] = intensity
        if (scent == null) {
            scan.take("شماره|کانال|نوع|گزینه")
            scan.number()?.let {
                if (it !in 1.0..3.0 || it % 1 != 0.0) return null
                values["channel"] = it.toInt()
            }
        }
        scan.power()?.let { values["enabled"] = it }
        if (scan.take("پخش")) {
            values["enabled"] = true
            while(scan.take(odorTargets.joinToString("|"))) { }
        }
        scan.take("با")
        scan.levelUnits()
        if (!scan.finished() || values.isEmpty()) return null
        // Preserve the existing single-slot API except confirmed named channel mapping.
        val action = if(values.size > 1) RahaAction.CONFIGURE_FRAGRANCE else when(values.keys.single()) {
            "enabled" -> RahaAction.SET_FRAGRANCE_POWER
            "channel" -> RahaAction.SET_FRAGRANCE_CHANNEL
            "intensity" -> RahaAction.SET_FRAGRANCE_INTENSITY
            else -> RahaAction.SET_FRAGRANCE_DURATION
        }
        val summary = values.map { (key, value) -> when(key) {
            "enabled" -> if(value == true) "روشن کردن خوشبوکننده" else "خاموش کردن خوشبوکننده"
            "channel" -> "انتخاب رایحه $value"
            "durationMinutes" -> if(value == 0) "مدت نامحدود" else "مدت $value دقیقه"
            else -> "شدت " + mapOf("low" to "ضعیف", "medium" to "متوسط", "high" to "قوی")[value]
        }}.joinToString("؛ ")
        return Recognition.Matched(VehicleCommand(action.name, action.name, values, summary))
    }

    private fun describe(action: RahaAction, p: Map<String, Any>): String = when(action) {
        RahaAction.SET_FRAGRANCE_POWER -> "خوشبوکننده: ${if(p["enabled"] == true) "روشن" else "خاموش"}"
        RahaAction.SET_FRAGRANCE_CHANNEL -> {
            val name = RahaFragranceVocabulary.scents.firstOrNull { it.id == p["channel"] }?.label
            if (name != null) "انتخاب رایحه $name" else "انتخاب رایحه شمارهٔ ${p["channel"]}"
        }
        RahaAction.SET_FRAGRANCE_INTENSITY -> "شدت رایحه: ${mapOf("low" to "ضعیف", "medium" to "متوسط", "high" to "قوی")[p["intensity"]]}"
        RahaAction.ADJUST_FRAGRANCE_INTENSITY -> "تغییر شدت رایحه: ${p["delta"]} پله"
        RahaAction.SAVE_SEAT_POSITION -> "ذخیره یا به‌روزرسانی موقعیت صندلی راننده در حالت Driver"
        RahaAction.RECALL_SEAT_POSITION -> "بازیابی موقعیت صندلی راننده از حالت Driver"
        else -> action.name
    }
}
