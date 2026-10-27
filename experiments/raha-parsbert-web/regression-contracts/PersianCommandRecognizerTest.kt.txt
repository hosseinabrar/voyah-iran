package com.abrar.voicecommands

import org.junit.Assert.*
import org.junit.Test

class PersianCommandRecognizerTest {
    private val parser = PersianCommandRecognizer()
    private fun match(text: String): VehicleCommand {
        val result = parser.recognize(text)
        assertTrue("$text -> $result", result is Recognition.Matched)
        return (result as Recognition.Matched).command
    }
    @Test fun everyHighlightedRowHasAnExample() {
        val examples = mapOf(
            "VC003" to "لطفا صدا رو زیاد کن", "VC004" to "روشنایی صفحه رو کمتر کن",
            "VC005" to "آهنگ بعدی", "VC006" to "پخش رو ادامه بده",
            "VC034" to "صندلی عقب رو نشون بده", "VC035" to "حالت کمپ", "VC036" to "حالت استراحت",
            "VC037" to "استراحت ده دقیقه ای", "VC039" to "نورپردازی رو روشن کن",
            "VC040" to "نورپردازی بیشتر", "VC041" to "نور رو با موسیقی هماهنگ کن",
            "VC042" to "مه شکن عقب روشن", "VC043" to "چراغ خودکار خاموش",
            "VC044" to "چراغ ها روشن", "VC045" to "نوربالای هوشمند روشن", "VC046" to "نور پایین خاموش",
            "VC047" to "اکو", "VC048" to "حالت رانندگی کامفورت", "VC049" to "حالت شخصی", "VC050" to "اسپورت",
            "VC051" to "ورود راحت روشن", "VC052" to "کولر رو خاموش کن", "VC053" to "تهویه خودکار",
            "VC054" to "دمای سمت راننده رو روی بیست و دو بذار", "VC055" to "دمای راننده رو دو درجه کم کن",
            "VC056" to "باد کولر رو بیشتر کن", "VC057" to "بخارگیر جلو روشن", "VC058" to "بخارگیر عقب خاموش",
            "VC059" to "گردش هوا رو روی داخل بذار", "VC060" to "دمای جلو رو یکسان کن",
            "VC061" to "خوشبوکننده روشن", "VC062" to "گرمکن صندلی راننده روشن",
            "VC063" to "تهویه صندلی شاگرد خاموش", "VC064" to "ماساژ صندلی راننده روشن",
            "VC065" to "حافظه شماره دو صندلی", "VC066" to "گرمکن آینه روشن",
            "VC067" to "پنجره سمت راننده رو باز کن", "VC068" to "همه پنجره ها رو باز کن",
            "VC069" to "شیشه ها رو تهویه کن", "VC070" to "پرده سقف رو ببند",
            "VC071" to "صندوق عقب رو باز کن", "VC072" to "قفل کودک رو غیر فعال کن",
            "VC073" to "درب باک رو باز کن", "VC074" to "درب شارژ باز",
            "VC075" to "بازیابی انرژی متوسط", "VC076" to "برگرد"
        )
        assertEquals(SupportedCatalog.ids, examples.keys)
        examples.forEach { (id, phrase) -> assertEquals(phrase, id, match(phrase).catalogId) }
    }
    @Test fun windowSynonymsOrderAndDefault() {
        for (phrase in listOf("پنجره رو باز کن", "پنجره ها رو باز کن", "لطفاً پنجره‌ها رو باز کن", "همه پنجره ها رو باز کن", "باز کردن پنجره ها", "شیشه هارو بده پایین")) {
            assertEquals(phrase, "window.all.set", match(phrase).action)
            assertEquals("open", match(phrase).parameters["position"])
        }
        for (phrase in listOf("لطفا پنجره سمت راننده رو باز کن", "باز کن شیشه راننده رو", "شیشه راننده رو بده پایین")) {
            assertEquals("window.position.set", match(phrase).action)
            assertEquals("driver", match(phrase).parameters["window"])
        }
        assertEquals("rear_left", match("پنجره عقب چپ رو ببند").parameters["window"])
        assertEquals("passenger", match("شیشه شاگرد رو بالا ببر").parameters["window"])
    }
    @Test fun extractsTypedNumbersAndTargets() {
        assertEquals(mapOf("zone" to "driver", "direction" to "down", "amountCelsius" to 2.0), match("دمای راننده رو دو درجه کم کن").parameters)
        assertEquals(mapOf("zone" to "passenger", "celsius" to 22.5), match("دمای شاگرد روی ۲۲٫۵").parameters)
        assertEquals(22.5, match("دما بیست و دو و نیم درجه").parameters["celsius"])
        assertEquals(2, match("حافظه صندلی ۲").parameters["slot"])
        assertEquals(false, match("قفل کودک غیر فعال").parameters["enabled"])
    }
    @Test fun rejectsNegationQuestionsExceptionsAndUnselectedActions() {
        val phrases = listOf(
            "پنجره رو باز نکن", "شیشه راننده رو نبند", "پنجره بازه؟", "پنجره باز است",
            "اگر بارون اومد پنجره رو ببند", "همه شیشه ها به جز راننده بسته", "پنجره رو نصفه باز کن",
            "فقط پنجره راننده رو باز کن و موسیقی رو پخش کن", "پنجره رو ببند و سانروف رو باز کن",
            "همه پنجره های راننده رو باز کن", "پنجره راننده شاگرد باز", "پنجره عقب باز",
            "پنجره رو باز و بسته کن", "اکو اسپورت", "اکو خاموش", "صدا رو زیاد و کم کن",
            "نورپردازی خاموش", "نورپردازی کمتر", "مه شکن عقب خاموش", "ورود راحت خاموش",
            "نورپردازی بنفش", "بلوتوث روشن", "به علی زنگ بزن", "برو تهران", "رادیو روشن",
            "سانروف باز", "درب شارژ بسته", "درب باک بسته", "تهویه خودکار خاموش",
            "استراحت بیست دقیقه", "حافظه صندلی ۱٫۵", "حافظه صندلی ۸", "دما ۴۲", "دما ۲۲ ۲۴",
            "ماساژ صندلی عقب روشن", "صندلی روشن", "پخش", "باز", "شیشه", "سلام", "",
            "قفل کودک رو روشن نکن لطفا", "دوربین عقب رو ببند", "بازیابی ضعیف قوی",
            "پنجره راننده فردا باز", "پنجره راننده رو باز کن شاید", "ماساژ صندلی روشن"
        )
        phrases.forEach { assertTrue("$it -> ${parser.recognize(it)}", parser.recognize(it) is Recognition.Unknown) }
    }
    @Test fun selectionCanBeNarrowedWithoutAndroid() {
        val narrowed = PersianCommandRecognizer(setOf("VC047"))
        assertTrue(narrowed.recognize("اکو") is Recognition.Matched)
        assertTrue(narrowed.recognize("اسپورت") is Recognition.Unknown)
    }
}
