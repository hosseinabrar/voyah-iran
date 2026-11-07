package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaSeatRecallTest {
    @Test fun unseenPlacementFormsRecallTheRequestedMemory() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(noun in listOf("صندلی","صندلی راننده","صندلیم")) for(context in listOf("حالت","موقعیت","وضعیت","حافظه"))
            for((number,slot) in mapOf("یک" to "driver","دو" to "2","سه" to "3"))
                for(template in listOf("%s را به %s %s ببر","لطفا %s رو تو %s %s قرار بده برام","%s رو بذار تو %s %s")) {
                    val text=template.format(noun,context,number);val r=parser.recognize(text)
                    assertTrue("$text => $r",r is Recognition.Matched)
                    val c=(r as Recognition.Matched).command
                    assertEquals("RECALL_SEAT_POSITION",c.action);assertEquals(mapOf("seat" to "driver","slot" to slot),c.parameters)
                }
    }
    @Test fun savingIsSeparateAndInvalidProfilesDoNotMoveTheSeat() {
        val parser=OldRahaRecognizer { "".byteInputStream() }
        for(text in listOf("حافظه صندلی دو رو آپدیت کن","موقعیت صندلی دو رو ذخیره کن")) {
            val r=parser.recognize(text) as Recognition.Matched
            assertEquals("SAVE_SEAT_POSITION",r.command.action);assertEquals("2",r.command.parameters["slot"])
        }
        for(text in listOf("صندلی رو بذار تو حالت چهار","صندلی رو بذار تو حالت","صندلی شاگرد رو بذار تو حالت دو","صندلی رو بذار روی دو و سه"))
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
    }
}
