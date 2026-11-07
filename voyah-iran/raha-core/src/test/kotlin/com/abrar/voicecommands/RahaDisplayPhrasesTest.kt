package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Test
import org.junit.Assert.*

class RahaDisplayPhrasesTest {
    @Test fun rearDisplayLockAndPowerAliases() {
        val phrases = mapOf(
            true to listOf("نمایشگر عقب رو قفل کن", "نمایشگر عقف رو غیر فعال کن", "نمایشگر عقب رو دی اکتیو کن", "مانیتور عقب را غیرفعال کنید", "صفحه نمایش عقب رو خاموش کن", "قفل نمایشگر عقب رو فعال کن", "نمایشگر عقب رو ببند"),
            false to listOf("نمایشگر عقب رو فعال کن", "نمایشگر عقب رو اکتیو کن", "نمایشگر عقب رو روشن کن", "قفل نمایشگر عقب رو غیر فعال کن", "قفل نمایشگر عقب را باز کنید", "قفل مانیتور عقب رو بردار", "نمایشگر عقب رو باز کن")
        )
        for ((enabled, texts) in phrases) for (phrase in texts) {
            val result = RahaCommandRecognizer().recognize(phrase)
            assertTrue("$phrase -> $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(phrase, "SET_REAR_DISPLAY_LOCK", command.action)
            assertEquals(phrase, mapOf("enabled" to enabled), command.parameters)
        }
    }
    @Test fun displayCommands() {
        val cases = listOf(
            Triple("روشنایی نمایشگر ها رو زیاد کن", "SET_DISPLAY_BRIGHTNESS", mapOf("display" to "all", "deltaPercent" to 10)),
            Triple("نور نمایشگرها رو کم کن", "SET_DISPLAY_BRIGHTNESS", mapOf("display" to "all", "deltaPercent" to -10)),
            Triple("نور نمایشگر شاگرد رو زیاد کن", "SET_DISPLAY_BRIGHTNESS", mapOf("display" to "passenger", "deltaPercent" to 10)),
            Triple("نور نمایشگر رو روی پنجاه درصد بذار", "SET_DISPLAY_BRIGHTNESS", mapOf("display" to "central", "percent" to 50)),
            Triple("نمایشگر رو بذار تو حالت روز", "SET_DISPLAY_THEME_MODE", mapOf("mode" to "day")),
            Triple("نمایشگر رو بذار تو حالت شب", "SET_DISPLAY_THEME_MODE", mapOf("mode" to "night")),
            Triple("نمایشگر رو بذار تو حالت خودکار", "SET_DISPLAY_THEME_MODE", mapOf("mode" to "auto"))
        )
        for ((phrase, action, parameters) in cases) {
            val result = RahaCommandRecognizer().recognize(phrase)
            assertTrue("$phrase -> $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(phrase, action, command.action)
            assertEquals(phrase, parameters, command.parameters)
        }
    }

    @Test fun collisionSensitivityAndClusterAliases() {
        fun check(text:String,action:String,parameters:Map<String,Any>) {
            val result=RahaCommandRecognizer().recognize(text)
            assertTrue("$text -> $result",result is Recognition.Matched)
            val command=(result as Recognition.Matched).command
            assertEquals(text,action,command.action)
            assertEquals(text,parameters,command.parameters)
        }
        for((word,level) in mapOf("کم" to "min","متوسط" to "medium","زیاد" to "max")) {
            for(target in listOf("رادار تصادف جلو","سنسور برخورد جلو","هشدار تصادف از جلو")) {
                check("حساسیت $target رو بذار روی $word","SET_FORWARD_COLLISION_WARNING",mapOf("sensitivity" to level))
                check("$target رو $word کن","SET_FORWARD_COLLISION_WARNING",mapOf("sensitivity" to level))
            }
        }
        for(target in listOf("کلاستر","داشبورد","پشت آمپر")) {
            if(target=="نمایشگر راننده")continue
            check("نور $target رو تا انتها زیاد کن","SET_DISPLAY_BRIGHTNESS",mapOf("display" to "cluster","percent" to 100))
            check("روشنایی $target را تا انتها کم کنید","SET_DISPLAY_BRIGHTNESS",mapOf("display" to "cluster","percent" to 0))
        }
    }

    @Test fun swapDisplayApps() {
        for(text in listOf("محتوای نمایشگر ها رو جا به جا کن","نمایشگر ها رو جا به جا کن","مانیتورها رو جا به جا کن","مانیتور ها رو سوئیچ کن","برنامه ها رو سوئیچ کن","برنامه ها رو جا به جا کن","برنامه مانیتورها رو عوض کن","محتوای مانیتورها را جابه‌جا کنید","برنامه‌ها را با هم عوض کنید")) {
            val result=RahaCommandRecognizer().recognize(text)
            assertTrue("$text -> $result",result is Recognition.Matched)
            assertEquals(text,"SWAP_DISPLAY_APPS",(result as Recognition.Matched).command.action)
            assertTrue(result.command.parameters.isEmpty())
        }
    }
}
