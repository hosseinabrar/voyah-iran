package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaDoorAliasesTest {
 @Test fun singularAndContractedDoorNamesUseCentralLock() {
  val parser=RahaCommandRecognizer()
  for(noun in listOf("در رو","درب رو","درارو","درا رو","درهارو","درها رو","دررو","درو","دربو","دربارو","درها را"))
  for((verb,locked) in listOf("قفل کن" to true,"قفل کنید" to true,"باز کن" to false)) {
   val text="$noun $verb"
   val result=parser.recognize(text)
   assertTrue("$text -> $result",result is Recognition.Matched)
   val cmd=(result as Recognition.Matched).command
   assertEquals(text,"SET_DOOR_LOCK",cmd.action)
   assertEquals(text,mapOf("door" to "all","locked" to locked),cmd.parameters)
  }
 }
 @Test fun prepositionAndSpecificFlapsAreNotCentralDoors() {
  val parser=RahaCommandRecognizer()
  for((text,action) in listOf("در باک رو باز کن" to "OPEN_FUEL_FLAP",
   "در صندوق رو باز کن" to "OPEN_TAILGATE","در شارژ رو باز کن" to "OPEN_CHARGE_PORT",
   "قفل کودک را در حالت فعال قرار دهید" to "SET_CHILD_LOCK")) {
   val result=parser.recognize(text)
   assertTrue("$text -> $result",result is Recognition.Matched)
   assertEquals(text,action,(result as Recognition.Matched).command.action)
  }
 }
 @Test fun everyPassengerDoorDirectionUsesCentralLock() {
  val parser=RahaCommandRecognizer()
  for(noun in listOf("در","درب","درها","درا"))
  for(side in listOf("راننده","شاگرد","چپ","راست","عقب","جلو","عقب چپ","عقب راست","سرنشین","ردیف دوم"))
  for((verb,locked) in listOf("قفل کن" to true,"باز کن" to false,"باز کنید" to false)) {
   val text="$noun $side رو $verb"
   val result=parser.recognize(text)
   assertTrue("$text -> $result",result is Recognition.Matched)
   val cmd=(result as Recognition.Matched).command
   assertEquals(text,"SET_DOOR_LOCK",cmd.action)
   assertEquals(text,mapOf("door" to "all","locked" to locked),cmd.parameters)
  }
 }
}
