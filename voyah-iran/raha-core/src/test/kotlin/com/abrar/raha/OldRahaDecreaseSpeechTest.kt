package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaDecreaseSpeechTest {
    @Test fun misheardDecreaseKeepsTargetAndAmount() {
        for(parser in listOf(OldRahaRecognizer(),OldRahaRecognizer { "".byteInputStream() })) {
            for(prefix in listOf("صدا رو","دما رو","فن رو","گرمکن صندلی راست رو","سردکن صندلی راننده رو","ماساژور رو","نور داخل رو","حساسیت سنسور بارون رو","ارتفاع ماشین رو","روشنایی نمایشگر وسط رو","صدا رو دو تا","گرمکن صندلی راست رو دو تا")) {
                val expected=parser.recognize("$prefix کم کن")
                assertTrue("$prefix => $expected",expected is Recognition.Matched)
                for(suffix in listOf("کن کن","کن")) {
                    val actual=parser.recognize("$prefix $suffix")
                    assertTrue("$prefix $suffix => $actual",actual is Recognition.Matched)
                    val c=(actual as Recognition.Matched).command;val e=(expected as Recognition.Matched).command
                    assertEquals(prefix,e.action,c.action);assertEquals(prefix,e.parameters.mapValues { (_,v) -> if(v is Number) v.toDouble() else v },c.parameters.mapValues { (_,v) -> if(v is Number) v.toDouble() else v })
                }
            }
        }
    }
    @Test fun ordinaryImperativesAreNeverRewritten() {
        for(text in listOf("گرمکن رو روشن کن","صندلی رو گرم کن","ماشین رو خنک کن","سانروف رو باز کن","تهویه رو فعال کن","شارژر رو روشن کن","صدا رو زیاد کن","چراغ رو خاموش کن","صندلی رو آپدیت کن"))
            assertTrue(text,RahaDecreaseSpeechRepair.candidates(text).isEmpty())
        val parser=OldRahaRecognizer()
        for(text in listOf("پیتزا رو کن کن","سانروف رو گرم کن","درخت رو کن")) assertTrue(text,parser.recognize(text) is Recognition.Unknown)
    }
}
