package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import com.abrar.raha.RahaPhaseSixRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaPhaseSixSpeechRegressionTest {
    private val parser=RahaCommandRecognizer()
    private fun check(lines:String, action:String, p:Map<String,Any>) {
        val failures=lines.trimIndent().lines().filter { it.isNotBlank() }.mapNotNull { text ->
            val r=parser.recognize(text)
            if(r is Recognition.Matched && r.command.action==action && r.command.parameters==p)null else "$text -> $r; expected=$action $p"
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun userEnergySentences() {
        check("""
            ماشین رو بذار تو مود هایبرید
            ماشین رو بذار تو مد هیبرید
            مصرف سوخت ترکیبی رو فعال کن
            هایبرید
            هیبرید
            حالت ترکیبی
        ""","SET_ENERGY_STRATEGY",mapOf("strategy" to "hybrid"))
        check("""
            ماشین رو بذار تو حالت تمام الکتریکی
            حالت تمام برقی رو فعال کن
            فعال کردن مود برقی
            اول باتری رو مصرف کن
            اول از باتری استفاده کن
        ""","SET_ENERGY_STRATEGY",mapOf("strategy" to "electric"))
        check("فقط برق استفاده کن","SET_ENERGY_STRATEGY",mapOf("strategy" to "electric"))
        check("""
            فقط بنزین بسوزون
            اول بنزین
            اول بنزین رو مصرف کن
            اول بنزین بسوزون
            اول از بنزین استفاده کن
        ""","SET_ENERGY_STRATEGY",mapOf("strategy" to "fuel"))
        check("""
            ماشین رو بذار تو حالت ذخیره
            حالت ذخیره انرژی رو فعال کن
            ذخیره باتری رو فعال کن
            ماشین رو بذار تو مود سیو
        ""","SET_ENERGY_STRATEGY",mapOf("strategy" to "save"))
        check("ذخیره باتری رو به میزان ۵۰ درصد فعال کن","SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to 50))
        check("سیو باتری ۷۰ درصد\nسیو ۷۰","SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to 70))
    }
    @Test fun userSceneSentences() {
        check("""
            ماشین رو بذار تو مود کارواش
            کارواش
            شست و شو
            شست و شوی ماشین
            مود شست و شو رو فعال کن
            سناریو کارواش رو فعال کن
        ""","ACTIVATE_SCENE",mapOf("scene" to "carWash"))
        check("از سناریو خارج شو\nسناریو رو غیر فعال کن","DEACTIVATE_SCENE",mapOf("scene" to "current"))
        check("کارواش رو غیر فعال کن\nاز مود کارواش خارج شو","DEACTIVATE_SCENE",mapOf("scene" to "current"))
        for((scene,aliases) in RahaPhaseSixRecognizer.scenes)for(noun in aliases.split('|')) {
            check("""
                $noun رو غیر فعال کن
                سناریوی $noun را ببندید
                لطفا از حالت $noun خارج شوید
                بستن سناریوی $noun
                $noun رو خاموش کن
                $noun رو دی اکتیو کن
            ""","DEACTIVATE_SCENE",mapOf("scene" to "current"))
        }
    }
    @Test fun userChargeSentences() {
        check("""
            شارژ رو قطع کن
            توقف شارژ
            شارژر رو قطع کن
            شارژر رو آزاد کن
            شارژ رو متوقف کن
        ""","STOP_CHARGING",emptyMap())
        check("""
            کنترل دمای باتری رو فعال کن
            دمای باتری رو بالا ببر
            دمای باتری رو ببر بالا
            باتری ها رو گرم کن
            دمای باتری رو کنترل کن
            کنترل دمای باتری ها رو فعال کن
            لطفا باتری‌ها را گرم کنید
            گرمایش باتری را فعال کنید
            مدیریت دمای باتری را روشن کن
            پیش گرمایش باتری را فعال کن
        ""","SET_BATTERY_PRECONDITIONING",mapOf("enabled" to true))
        check("کنترل دمای باتری رو غیر فعال کن\nگرمایش باتری ها رو خاموش کن\nکنترل دمای باتری رو دی اکتیو کن", "SET_BATTERY_PRECONDITIONING", mapOf("enabled" to false))
    }
    @Test fun userMirrorSentences() {
        check("آیینه ها رو جمع کن\nآیینه ها رو ببند\nآیینه رو جمع کن","SET_MIRROR_FOLD",mapOf("side" to "all","folded" to true))
        check("آیینه ها رو باز کن\nبازکردن آیینه\nآیینه باز شود","SET_MIRROR_FOLD",mapOf("side" to "all","folded" to false))
    }
    @Test fun userPowerHoldSentences() {
        check("باتری رو برای ۳۰ دقیقه فعال نگه دار","ACTIVATE_SCENE",mapOf("scene" to "powerKeep","minutes" to 30))
        check("ماشین رو فعال نگه دار\nماشین روشن بمونه","ACTIVATE_SCENE",mapOf("scene" to "powerKeep"))
        check("ماشین برای یک ساعت روشن بمونه","ACTIVATE_SCENE",mapOf("scene" to "powerKeep","minutes" to 60))
        for(noun in listOf("ماشین","خودرو","باتری"))for(verb in listOf("روشن نگه دار","فعال نگه دارید","روشن بماند"))
            check("لطفا $noun را برای نود دقیقه $verb","ACTIVATE_SCENE",mapOf("scene" to "powerKeep","minutes" to 90))
    }
    @Test fun generatedSaveTargetsAndWordOrder() {
        for(n in 25..80 step 5)for(noun in listOf("سیو","ذخیره باتری","حالت ذخیره انرژی")) {
            check("$noun $n درصد\n$n درصد $noun رو فعال کن\nلطفا $noun را روی $n درصد تنظیم کنید",
                "SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to n))
        }
        for(s in listOf("سیو ۸۵","سیو ۳۳","ماشین برای ۵ ساعت روشن بمونه"))
            assertTrue(s,parser.recognize(s) is Recognition.Unknown)
    }
}
