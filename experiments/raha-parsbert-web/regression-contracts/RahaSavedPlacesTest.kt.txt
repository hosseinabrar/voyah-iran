package com.abrar.voicecommands

import com.abrar.raha.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RahaSavedPlacesTest {
    private fun place(id:String="home",name:String="خونه",aliases:List<String> = listOf("خانه","منزل"))=RahaSavedPlace("user_$id",name,aliases,35.7,51.4)
    @After fun reset() {RahaNavigationRecognizer.updatePlaces(emptyList())}
    @Test fun dynamicNamesReachRecognitionAndSpeechGrammar() {
        RahaNavigationRecognizer.updatePlaces(listOf(place()))
        for(text in listOf("بریم خونه","خانه رو مسیریابی کن","منو ببر منزل")) {
            val result=RahaCommandRecognizer().recognize(text) as Recognition.Matched
            assertEquals("user_home",result.command.parameters["destination"])
        }
        assertTrue("بریم خونه" in RahaSpeechVocabulary.phrases())
        assertTrue("رها بریم خونه" in RahaSpeechVocabulary.wakePhrases())
        RahaNavigationRecognizer.updatePlaces(emptyList())
        assertFalse(RahaCommandRecognizer().recognize("بریم خونه") is Recognition.Matched)
    }
    @Test fun renameInvalidatesOldAliasesAndLongestNameWins() {
        RahaNavigationRecognizer.updatePlaces(listOf(place(),place("mother","خونه مادر",emptyList())))
        assertEquals("user_mother",(RahaCommandRecognizer().recognize("بریم خونه مادر") as Recognition.Matched).command.parameters["destination"])
        val revision=RahaNavigationRecognizer.revision
        RahaNavigationRecognizer.updatePlaces(listOf(place(name="محل کار",aliases=emptyList())))
        assertTrue(RahaNavigationRecognizer.revision>revision)
        assertFalse(RahaCommandRecognizer().recognize("بریم خونه") is Recognition.Matched)
        assertEquals("user_home",(RahaCommandRecognizer().recognize("بریم محل کار") as Recognition.Matched).command.parameters["destination"])
    }
    @Test fun limitsAndDuplicates() {
        assertEquals(listOf("خانه","منزل"),RahaSavedPlaces.aliases(" خانه ، منزل,خانه, "))
        assertThrows(IllegalArgumentException::class.java){RahaSavedPlaces.aliases("الف,ب,پ,ت,ث")}
        assertThrows(IllegalArgumentException::class.java){RahaSavedPlaces.validate(listOf(place(),place("other","منزل",emptyList())))}
        assertThrows(IllegalArgumentException::class.java){RahaSavedPlaces.validate(listOf(place(name="نمایندگی مرکزی")))}
        assertThrows(IllegalArgumentException::class.java){RahaSavedPlaces.validate(listOf(place().copy(latitude=Double.NaN)))}
        val eighteen=(1..18).map {place("$it","مکان $it",emptyList())}
        RahaSavedPlaces.validate(eighteen)
        assertThrows(IllegalArgumentException::class.java){RahaSavedPlaces.validate(eighteen+place())}
    }
}
