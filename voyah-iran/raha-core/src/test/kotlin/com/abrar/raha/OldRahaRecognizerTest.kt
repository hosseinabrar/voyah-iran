package com.abrar.raha

import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class OldRahaRecognizerTest {
    private fun parser()=OldRahaRecognizer { "".byteInputStream() }
    private fun check(text:String,action:String,params:Map<String,Any>) {
        val result=parser().recognize(text)
        assertTrue("$text -> $result",result is Recognition.Matched)
        val c=(result as Recognition.Matched).command
        assertEquals(action,c.action);assertEquals(params,c.parameters)
    }
    @Test fun newlyAddedControlsHaveRulesWithoutSavedExamples() {
        check("ایزی انتری رو خاموش کن","SET_EASY_ENTRY",mapOf("enabled" to false))
        check("ایزی اگزیت رو بذار تو حالت روشن","SET_EASY_EXIT",mapOf("enabled" to true))
        check("ماشین رو سر ساعت شارژ کن","SET_SCHEDULED_CHARGING",mapOf("enabled" to true))
        check("شارژر موبایل رو فعال کن","SET_WIRELESS_CHARGER",mapOf("enabled" to true))
        check("نمایشگر سمت راست رو خاموش کن","SET_PASSENGER_DISPLAY_POWER",mapOf("enabled" to false))
        check("ارتفاع ماشین رو بیشتر کن","ADJUST_SUSPENSION_HEIGHT",mapOf("delta" to 1))
        check("حساسیت هشدار برخورد رو کمتر کن","ADJUST_FORWARD_COLLISION_SENSITIVITY",mapOf("delta" to -1))
        check("تهویه شیشه ها و سانروف رو فعال کن","VENT_WINDOW",mapOf("window" to "allWithSunroof"))
        check("هوای داخل رو بچرخون","SET_AIR_RECIRCULATION",mapOf("mode" to "internal"))
    }
    @Test fun interiorLightAliasesKeepTheOldRelativeAction() {
        check("نورپردازی داخل اتاق رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1))
    }
    @Test fun unknownObjectsAndMissingStateDoNotExecute() {
        for(text in listOf("سانروف رو گرم کن","پیتزا رو گرم کن","ایزی انتری","شارژر وایرلس","شارژر وایرلس رو خاموش روشن کن"))
            assertTrue(text,parser().recognize(text) is Recognition.Unknown)
    }
    @Test fun learnedExamplesKeepParametersAndNegativeExamples() {
        val parser=OldRahaRecognizer()
        val window=parser.recognize("شیشه پشت سری راننده رو بکش پایین") as Recognition.Matched
        assertEquals("OPEN_WINDOW",window.command.action);assertEquals("rearLeft",window.command.parameters["window"])
        assertTrue(parser.recognize("سانروف رو گرم کن") is Recognition.Unknown)
        val contact=parser.recognize("یه زنگ به علی بزن") as Recognition.Matched
        assertEquals("علی",contact.command.parameters["contact_query"])
    }
}
