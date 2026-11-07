package com.abrar.raha
import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test
class OldRahaThermalSafetyTest {
    @Test fun modeWordsDoNotBecomeThermalComplaints() {
        for(parser in listOf(OldRahaRecognizer(),OldRahaRecognizer { "".byteInputStream() }))
            for(target in listOf("ماشین","کابین","اتاق")) for(mode in listOf("حالت","مود","وضعیت"))
                for(word in listOf("گرم","سرد","خنک")) {
                    for(text in listOf("$target رو بذار تو $mode $word","$mode $target بذار تو $mode $word ایندی وی جوال"))
                        assertTrue("$text",parser.recognize(text) is Recognition.Unknown)
                }
    }
    @Test fun fullRemainderMustBeUnderstood() {
        val parser=RahaCommandRecognizer()
        for(text in listOf("هوا گرمه ایندی وی جوال پرت و پلا","هوا گرمه کولر رو روشن کن موز","کولر روشن کن و ماساژ روشن کن سیب"))
            assertTrue(text,parser.recognize(text) is Recognition.Unknown)
        assertTrue(parser.recognize("هوا گرمه") is Recognition.Matched)
        assertTrue(parser.recognize("هوا خیلی گرمه و کولر روشن کن و ماساژ سطح سه") is Recognition.Matched)
        val drive=parser.recognize("ماشین رو بذار تو حالت ایندی وی جوال") as Recognition.Matched
        assertEquals("SET_DRIVE_MODE",drive.command.action)
        assertEquals("personal",drive.command.parameters["mode"])
    }
}
