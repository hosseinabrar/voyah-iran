package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaCabinSyncTest {
 @Test fun cabinTemperatureSyncAliases() {
  val parser=RahaCommandRecognizer()
  for(target in listOf("دمای کابین","دمای اتاق","دمای داخل ماشین","دمای داخل خودرو","دمای داخل","دمای ماشین","دمای خودرو"))
   for(verb in listOf("سینک کن","همگام کن","یکسان کن","هماهنگ کن")) {
    val phrase="$target رو $verb"
    val result=parser.recognize(phrase)
    assertTrue("$phrase: $result",result is Recognition.Matched)
    val command=(result as Recognition.Matched).command
    assertEquals(phrase,"SET_FRONT_CLIMATE_SYNC",command.action)
    assertEquals(phrase,mapOf("enabled" to true),command.parameters)
   }
 }
}
