package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaCameraPhrasesTest {
    @Test fun cameraPhrases() {
        val cases=mapOf(
            "OPEN_CAMERA" to listOf("دوربین رو باز کن", "برنامه دوربین رو باز کن", "بازکردن دوربین", "لطفا برنامه دوربین را باز کنید"),
            "TAKE_PHOTO" to listOf("یه عکس بگیر", "گرفتن عکس", "لطفا یک عکس بگیرید", "عکس بگیر", "یه عکس بنداز"),
            "START_VIDEO_RECORDING" to listOf("فیلم برداری رو شروع کن", "شروع فیلم برداری", "یه فیلم بگیر", "ضبط ویدیو رو شروع کن"),
            "STOP_VIDEO_RECORDING" to listOf("فیلم برداری رو متوقف کن", "ضبط رو قطع کن"),
            "SWITCH_CAMERA" to listOf("دوربین رو عوض کن", "تعویض دوربین", "دوربین را تغییر بده", "دوربین رو سوئیچ کن", "برو دوربین بعدی")
        )
        val failures=mutableListOf<String>()
        for((expected,phrases) in cases) for(phrase in phrases) {
            val result=RahaCommandRecognizer().recognize(phrase)
            if((result as? Recognition.Matched)?.command?.action!=expected)failures+="$phrase -> $result; expected=$expected"
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
