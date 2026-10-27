package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaWordOrderTest {
    private val parser=RahaCommandRecognizer()
    @Test fun reportedTemperaturePhrases() {
        val phrases=listOf("دما رو بذار رو بیست و سه درجه", "دما رو رو بیست و سه درجه تنظیم کن", "دما رو تنظیم کن رو بیست و سه درجه")
        for(text in phrases) {
            val result=parser.recognize(text)
            assertTrue("$text: $result",result is Recognition.Matched)
            val command=(result as Recognition.Matched).command
            assertEquals("SET_CABIN_TEMPERATURE",command.action)
            assertEquals(mapOf("zone" to "front","degreesC" to 23.0),command.parameters)
        }
    }
    @Test fun reorderedSlotsKeepTheSameActionAndParameters() {
        val examples=listOf(
            "دما" to "بیست و سه درجه",
            "دمای شاگرد" to "بیست و دو و نیم درجه",
            "سرعت فن" to "چهار",
            "گرمکن صندلی راننده" to "سطح دو",
            "سردکن صندلی شاگرد" to "سطح سه",
            "ماساژ صندلی راننده" to "موجی",
            "شدت رایحه" to "متوسط",
            "ارتفاع ماشین" to "بالا",
            "ماشین" to "اقتصادی",
            "حساسیت برف پاک کن" to "متوسط",
            "چراغ ها" to "اتوماتیک",
            "نورپردازی داخلی" to "حداکثر"
        )
        val failures=mutableListOf<String>()
        for((target,value) in examples) {
            val baseline=parser.recognize("$target رو روی $value تنظیم کن")
            if(baseline !is Recognition.Matched) { failures+="$target / $value baseline: $baseline";continue }
            for(text in listOf(
                "$target رو بذار رو $value",
                "$target رو رو $value تنظیم کن",
                "$target رو تنظیم کن رو $value",
                "روی $value تنظیم کن $target رو",
                "تنظیم کن $target رو روی $value",
                "$target را روی $value تنظیم نمایید",
                "لطفا روی $value $target را تنظیم کنید"
            )) {
                val actual=parser.recognize(text)
                if(actual !is Recognition.Matched || actual.command.action!=baseline.command.action || actual.command.parameters!=baseline.command.parameters)
                    failures+="$text -> $actual"
            }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun movementAndPowerWordOrder() {
        val failures=mutableListOf<String>()
        fun compare(reference:String,variants:List<String>) {
            val expected=parser.recognize(reference)
            if(expected !is Recognition.Matched) {failures+="$reference baseline: $expected";return}
            for(text in variants) {
                val actual=parser.recognize(text)
                if(actual !is Recognition.Matched || actual.command.action!=expected.command.action || actual.command.parameters!=expected.command.parameters)
                    failures+="$text -> $actual"
            }
        }
        for(target in listOf("شیشه راننده","شیشه شاگرد","شیشه ها","پرده","سانروف"))
            for(amount in listOf("کمی","تا نصف","تا نیمه","کاملا"))
                compare("$target رو $amount باز کن",listOf(
                    "$amount باز کن $target رو","باز کن $target رو $amount","$target را $amount باز کنید"))
        for(target in listOf("تهویه","کولر","بخاری","ای سی","گرمکن صندلی راننده","سردکن صندلی شاگرد","رایحه","نورپردازی داخلی"))
            for(verb in listOf("روشن کن","خاموش کن"))
                compare("$target رو $verb",listOf("$verb $target رو","لطفا $target را $verb"))
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun generatedSpokenExamplesRemainSupported() {
        val phrases=com.abrar.raha.RahaWordOrderCorpus.phrases()
        val failures=phrases.filter {parser.recognize(it) !is Recognition.Matched}
        assertTrue(failures.joinToString("\n"),failures.isEmpty())

    }
}
