package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Test
import org.junit.Assert.*
class RahaMediaPhrasesTest {
    @Test fun playPhrasesAreAvailableToSpeechDecoder() {
        val phrases = com.abrar.raha.RahaSpeechVocabulary.phrases()
        for (phrase in listOf("یه آهنگ پلی کن", "موزیک رو پلی کن", "موسیقی رو پلی کنید", "پلی کردن ترانه")) assertTrue(phrase, phrase in phrases)
    }
    @Test fun mediaPhrases() {
        val cases=mapOf(
            "PLAY_MEDIA" to listOf("یه آهنگ پلی کن", "موزیک رو پلی کن", "آهنگ رو پلی کن", "موسیقی رو پلی کن", "لطفا یک آهنگ پلی کنید", "پلی کردن موزیک", "یه موزیک پلی کن", "یه موسیقی پخش کن", "پخش موزیک", "موزیک رو پخش کن", "موسیقی رو اجرا کن", "آهنگ پخش کن", "آهنگ رو وصل کن", "لطفا موسیقی را پخش کنید", "پخش موسیقی را شروع کنید", "موزیک را ادامه بده", "آهنگ پلی", "پلی کن"),
            "PAUSE_MEDIA" to listOf("آهنگ رو پاز کن", "موزیک رو متوقف کن", "پخش موسیقی رو متوقف کن", "موزیک رو قطع کن", "پخش آهنگ رو متوقف کن", "لطفا پخش موسیقی را قطع نمایید", "آهنگ را متوقف کنید", "موزیک رو نگه دار", "پاز", "پخش موزیک را متوقف سازید"),
            "NEXT_MEDIA" to listOf("بزن آهنگ بعدی", "برو آهنگ بعدی", "آهنگ بعدی رو پخش کن", "لطفا آهنگ بعدی را اجرا کنید", "موزیک بعدی", "ترانه بعدی رو بذار", "یه آهنگ دیگه پخش کن", "موزیک رو عوض کن", "یه موسیقی دیگه پلی کن", "آهنگ را تغییر بده", "موسیقی را تعویض کنید", "یک آهنگ دیگر پخش کنید", "ترانه دیگری اجرا کن", "یه آهنگ جدید بذار", "موزیک متفاوت پخش کن", "آهنگ عوض کن", "آهنگو عوض کن"),
            "PREVIOUS_MEDIA" to listOf("برگرد به آهنگ قبلی", "آهنگ قبلی رو پخش کن", "برگردید به موسیقی قبلی", "لطفا ترانه قبلی را اجرا کنید", "آهنگ پیشین را پخش کنید", "آهنگ رو به قبلی عوض کن")
        )
        val failures=mutableListOf<String>()
        for((expected,phrases) in cases) for(phrase in phrases) {
            val result=RahaCommandRecognizer().recognize(phrase)
            if((result as? Recognition.Matched)?.command?.action!=expected) failures+="$phrase -> $result (expected $expected)"
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
