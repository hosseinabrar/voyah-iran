package com.abrar.raha

import org.junit.Assert.assertTrue
import org.junit.Test

class RahaEasyAccessVocabularyTest {
    @Test fun easyAccessAliasesAndStatesAreAvailableToProductionVosk() {
        val phrases=RahaSpeechVocabulary.phrases().toSet()
        for (noun in listOf("ورود آسان","خروج آسان","ایزی اگزیت","ایزی انتری","ایزی اینتری",
            "شارژ زمانبندی شده", "زمان بندی شارژ", "شارژ برنامه ریزی شده", "اسک جول شارژ")) {
            assertTrue(noun, noun in phrases)
            for (state in listOf("روشن","خاموش","فعال","غیر فعال")) {
                assertTrue("$noun / $state", "$noun رو $state کن" in phrases)
                assertTrue("$noun / $state", "$noun رو بذار تو حالت $state" in phrases)
            }
        }
    }
}
