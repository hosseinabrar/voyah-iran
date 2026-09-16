package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaAirflowAliasesTest {
 @Test fun outletsAcceptFrontFaceFeetAndPreserveWindshield() {
  val parser=RahaCommandRecognizer()
  for(noun in listOf("دریچه باد","دریچه تهویه","دریچه کولر","جهت باد","باد"))
  for((direction,expected) in listOf("رو به رو" to "face","روبه‌رو" to "face","روبرو" to "face",
   "جلو" to "face","صورت" to "face","به سمت پا" to "feet","شیشه جلو" to "windshield",
   "صورت و پا" to "faceAndFeet","شیشه جلو و پا" to "windshieldAndFeet"))
  for(ending in listOf("","رو فعال کن","را فعال نمایید")) {
   val text="$noun $direction $ending"
   val result=parser.recognize(text)
   assertTrue("$text -> $result",result is Recognition.Matched)
   val cmd=(result as Recognition.Matched).command
   assertEquals(text,"SET_AIRFLOW_DIRECTION",cmd.action)
   assertEquals(text,mapOf("zone" to "front","direction" to expected),cmd.parameters)
  }
 }
}
