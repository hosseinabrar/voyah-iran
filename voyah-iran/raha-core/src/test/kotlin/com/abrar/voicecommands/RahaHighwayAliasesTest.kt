package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaHighwayAliasesTest {
    @Test fun highwaySynonyms() {
        val recognizer=RahaCommandRecognizer()
        for(noun in listOf("تعلیق","ارتفاع"))
            for(adj in listOf("تطبیقی","اتوماتیک","اتومات","خودکار","هوشمند","تطبیق پذیر","آداپتیو","اداپتیو"))
                for(road in listOf("بزرگراه","اتوبان"))
                    for((ending,enabled) in listOf("رو رو فعال کن" to true,"را غیر فعال کن" to false,"اکتیو" to true)) {
                        val phrase="$noun $adj $road $ending"
                        val result=recognizer.recognize(phrase)
                        assertTrue("$phrase => $result",result is Recognition.Matched)
                        val cmd=(result as Recognition.Matched).command
                        assertEquals("SET_HIGHWAY_SUSPENSION_ADAPTATION",cmd.action)
                        assertEquals(enabled,cmd.parameters["enabled"])
                    }
    }
}
