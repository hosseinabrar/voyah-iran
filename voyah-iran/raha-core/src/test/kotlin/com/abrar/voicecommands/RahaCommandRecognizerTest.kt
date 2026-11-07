package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import com.abrar.raha.RahaAction
import org.junit.Assert.*
import org.junit.Test

class RahaCommandRecognizerTest {
    private val parser=RahaCommandRecognizer()
    private fun match(text:String, action:RahaAction, vararg parameters:Pair<String,Any>) {
        val result=parser.recognize(text)
        assertTrue("$text => $result",result is Recognition.Matched)
        val command=(result as Recognition.Matched).command
        assertEquals(text,action.name,command.action)
        assertEquals(text,parameters.toMap(),command.parameters)
    }
    @Test fun seatAliasesPreserveSideAndLevel() {
        for((side,target) in listOf("چپ" to "driver","سمت چپ" to "driver","راننده" to "driver","جلو چپ" to "driver","راست" to "passenger","سمت راست" to "passenger","شاگرد" to "passenger","سرنشین جلو" to "passenger")) {
            match("گرم کن صندلی $side رو روی سه بذار",RahaAction.SET_SEAT_HEATING,"seat" to target,"enabled" to true,"level" to 3)
            match("تهویه صندلی $side رو خاموش کن",RahaAction.SET_SEAT_VENTILATION,"seat" to target,"enabled" to false,"level" to 0)
            match("ماساژ صندلی $side رو روشن کن",RahaAction.SET_SEAT_MASSAGE,"seat" to target,"enabled" to true)
            match("ماساژ صندلی $side رو غلتکی کن",RahaAction.SET_SEAT_MASSAGE_PATTERN,"seat" to target,"pattern" to "rolling")
        }
    }
    @Test fun numericAndRelativeValues() {
        for(value in listOf("بیست و دو","بیست‌ودو","۲۲","٢٢","22"))
            match("دما رو بذار روی $value",RahaAction.SET_CABIN_TEMPERATURE,"zone" to "front","degreesC" to 22.0)
        match("دما رو بذار روی بیست و دو و نیم",RahaAction.SET_CABIN_TEMPERATURE,"zone" to "front","degreesC" to 22.5)
        match("سمت چپ رو دو درجه خنک‌تر کن",RahaAction.ADJUST_CABIN_TEMPERATURE,"zone" to "driver","delta" to -2.0)
        match("دمای سمت راست رو ببر بالا",RahaAction.ADJUST_CABIN_TEMPERATURE,"zone" to "passenger","delta" to 1.0)
        match("فن رو دو پله کم کن",RahaAction.ADJUST_FAN_SPEED,"zone" to "front","delta" to -2)
        match("فن رو روی بیشترین سرعت بذار",RahaAction.SET_FAN_SPEED,"zone" to "front","level" to "max")
    }
    @Test fun distinctClimateFeatures() {
        match("کولر رو روشن کن",RahaAction.SET_CLIMATE_POWER,"enabled" to true,"zone" to "front","acEnabled" to true,"temperatureMaxC" to 23.0)
        match("ای سی رو روشن کن",RahaAction.SET_AC_POWER,"enabled" to true)
        match("تهویه رو اتومات کن",RahaAction.SET_CLIMATE_AUTO,"enabled" to true,"zone" to "front")
        match("گردش هوا رو خودکار کن",RahaAction.SET_AIR_RECIRCULATION,"mode" to "auto")
        match("گردش داخلی رو خاموش کن",RahaAction.SET_AIR_RECIRCULATION,"mode" to "external")
        match("ورود هوای بیرون رو ببند",RahaAction.SET_AIR_RECIRCULATION,"mode" to "internal")
        match("باد رو به شیشه جلو بده",RahaAction.SET_AIRFLOW_DIRECTION,"direction" to "windshield","zone" to "front")
        match("بخار شیشه جلو رو بگیر",RahaAction.SET_FRONT_DEFROSTER,"enabled" to true)
        match("گرم کن شیشه عقب رو روشن کن",RahaAction.SET_REAR_DEFROSTER,"enabled" to true)
        match("دمای راننده و شاگرد یکی باشه",RahaAction.SET_FRONT_CLIMATE_SYNC,"enabled" to true)
    }
    @Test fun rearClimateIsUnsupportedButRearWindowDefrostRemains() {
        for(s in listOf("تهویه عقب رو روشن کن","فن عقب رو بذار روی دو","دما برای ردیف دوم ۲۲ درجه باشه","تهویه صندلی عقب رو روشن کن","باد کولر عقب رو زیاد کن"))
            assertTrue(s,parser.recognize(s) is Recognition.Unknown)
        match("بخارگیر عقب رو خاموش کن",RahaAction.SET_REAR_DEFROSTER,"enabled" to false)
    }
    @Test fun rejectsNegationConflictsQuestionsAndUnsupportedActions() {
        for(s in listOf("کولر رو روشن کن و خاموش کن","فن رو زیاد و کم کن","دما رو 22 و 24 کن","صندلی چپ و راست رو گرم کن","صندلی عقب چپ رو گرم کن","تهویه روشنه؟","دما چنده؟","اگر سرد شد تهویه رو روشن کن","دما رو 90 کن","فن رو منفی دو کن","ماساژ راننده رو روی 12 بذار","دما رو روی 22.3 بذار","لطفا","تهویه","گردش هوا"))
            assertTrue("$s => ${parser.recognize(s)}",parser.recognize(s) is Recognition.Unknown)
    }
    @Test fun politeRequestsAndIdempotence() {
        val s="رها می‌شه لطفاً تهویه رو روشن کنی؟"
        match(s,RahaAction.SET_CLIMATE_POWER,"enabled" to true,"zone" to "front","acEnabled" to false)
        assertEquals(parser.recognize(s),parser.recognize(s))
    }
    @Test fun colloquialClimateRequestsPreserveConditionalTemperature() {
        for(s in listOf("تهویه رو روشن کن","سیستم تهویه فعال بشه","تهویه را راه بنداز"))
            match(s,RahaAction.SET_CLIMATE_POWER,"zone" to "front","enabled" to true,"acEnabled" to false)
        for(s in listOf("کولر روشن بشه","کولر رو فعال کن","کولر رو راه بنداز"))
            match(s,RahaAction.SET_CLIMATE_POWER,"zone" to "front","enabled" to true,"acEnabled" to true,"temperatureMaxC" to 23.0)
        for(s in listOf("بخاری روشن بشه","بخاری رو فعال کن","بخاری رو راه بنداز"))
            match(s,RahaAction.SET_CLIMATE_POWER,"zone" to "front","enabled" to true,"acEnabled" to true,"temperatureMinC" to 26.0)
        match("کولر شاگرد رو روشن کن",RahaAction.SET_CLIMATE_POWER,"zone" to "passenger","enabled" to true,"acEnabled" to true,"temperatureMaxC" to 23.0)
        match("بخاری دو طرف رو روشن کن",RahaAction.SET_CLIMATE_POWER,"zone" to "front","enabled" to true,"acEnabled" to true,"temperatureMinC" to 26.0)
        for(s in listOf("کولر رو خاموش کن","بخاری رو خاموش کن","تهویه رو خاموش کن"))
            match(s,RahaAction.SET_CLIMATE_POWER,"zone" to "front","enabled" to false)
        // Recognition must not emit an unconditional setpoint, or depend on guessed car state.
        for(s in listOf("کولر رو روشن کن","بخاری رو روشن کن")) {
            val p=(parser.recognize(s) as Recognition.Matched).command.parameters
            assertFalse(p.containsKey("degreesC"))
            assertFalse(p.containsKey("currentTemperature"))
            assertNotEquals(p.containsKey("temperatureMinC"),p.containsKey("temperatureMaxC"))
        }
    }
    @Test fun missingSeatDefaultsToDriverAndExplicitSeatWins() {
        for(s in listOf("گرمکن رو فعال کن","گرم کن صندلی رو روشن کن","صندلیم رو گرم کن"))
            match(s,RahaAction.SET_SEAT_HEATING,"seat" to "driver","enabled" to true)
        match("سردکن رو روشن کن",RahaAction.SET_SEAT_VENTILATION,"seat" to "driver","enabled" to true)
        match("ماساژم بده",RahaAction.SET_SEAT_MASSAGE,"seat" to "driver","enabled" to true)
        match("ماساژ رو موجی کن",RahaAction.SET_SEAT_MASSAGE_PATTERN,"seat" to "driver","pattern" to "wave")
        match("گرمکن راست رو فعال کن",RahaAction.SET_SEAT_HEATING,"seat" to "passenger","enabled" to true)
        match("دمای دو طرف رو بذار روی ۲۳",RahaAction.SET_CABIN_TEMPERATURE,"zone" to "front","degreesC" to 23.0)
        match("تهویه صندلی رو روشن کن",RahaAction.SET_SEAT_VENTILATION,"seat" to "driver","enabled" to true)
        assertEquals(parser.recognize("بخاری رو روشن کن"), parser.recognize("بخاری رو روشن نکن"))
        assertTrue(parser.recognize("گرمکن همه سمت راست رو فعال کن") is Recognition.Unknown)
    }
    @Test fun phaseOneExamples() {
        val input=javaClass.getResourceAsStream("/raha-phase1-corpus.tsv") ?: error("Missing phase 1 corpus")
        input.bufferedReader(Charsets.UTF_8).useLines {lines->lines.filter{it.isNotBlank()}.forEach {line->
            val (phrase,expected)=line.split('\t')
            val result=parser.recognize(phrase)
            val actual=if(result is Recognition.Matched)result.command.action else "UNKNOWN"
            assertEquals("$phrase => $result",expected,actual)
        }}
    }
}
