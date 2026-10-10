package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

internal object RahaAppLaunchRecognizer {
    const val nouns = "نقشه|مسیریاب|مسیر یاب|نویگیشن|نشان|موزیک پلیر|موزیکپلیر|موسیقی|موزیک|آهنگ|پلیر|پخش|رادیو"
    const val verbs = "باز کردن|بازکردن|باز|اجرا|بیار بالا|بالا بیار|بیار|راه بنداز|راه بینداز|برو روی|برو رو|برو به|برید روی|بروید روی|بروید به"
    fun recognize(text: String): Recognition? {
        val scan = RahaCommandRecognizer.Scan(text)
        val app = scan.take("برنامه|اپلیکیشن|اپ|نرم افزار|نرم‌افزار")
        val radio = scan.take("رادیو")
        val map = scan.take("نقشه|مسیریاب|مسیر یاب|نویگیشن|نشان")
        val neshan = map && scan.last == "نشان"
        val player = if (!map) scan.take("موزیک پلیر|موزیکپلیر|پلیر|پخش") else false
        val music = if (!map) scan.take("موسیقی|موزیک|آهنگ") else false
        if (!radio && !map && !player && !music) return null
        if (!scan.take(if (map) "$verbs|نشون بده|نشان بده|نمایش بده" else verbs)) return null
        // Preserve “موسیقی رو اجرا کن” as playback unless opening an app is explicit.
        if (!radio && !map && !app && !player && scan.last == "اجرا") return null
        while (scan.take("کن|کنید|کنین|کردن|شود|بشه|بکن|بکنید|لطفا|برام|برای من|رو|را|و|یه|یک")) { }
        if (!scan.finished()) return null
        val action = if (radio) "OPEN_OEM_RADIO" else if (map) "OPEN_DEFAULT_MAP" else "OPEN_DEFAULT_MUSIC"
        return Recognition.Matched(VehicleCommand(action, action,
            if (neshan) mapOf("requiredApp" to "neshan") else emptyMap(),
            if (radio) "باز کردن رادیوی خودرو" else if (map) "باز کردن نقشهٔ پیش‌فرض" else "باز کردن موزیک‌پلیر پیش‌فرض"))
    }
}
