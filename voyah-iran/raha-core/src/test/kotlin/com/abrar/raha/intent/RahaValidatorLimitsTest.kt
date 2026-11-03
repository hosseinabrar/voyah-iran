package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
class RahaValidatorLimitsTest {
 private fun apply(text:String,action:String,key:String,value:Int,allowed:List<Int>)=RahaIntentGrounding.apply(text,action,mapOf(key to value),mapOf(key to allowed))
 @Test fun domainSpecificDecrease() {
  for((t,a) in listOf("صدا رو دو پله آروم ترش کن" to "ADJUST_AUDIO_VOLUME","آهنگ رو دو پله آهسته تر پخش کن" to "ADJUST_AUDIO_VOLUME","نور داخل رو دو درجه ملایم تر کن" to "ADJUST_AMBIENT_LIGHT_BRIGHTNESS")) {
   val r=apply(t,a,"delta",2,listOf(-2,2));assertNull(t,r.error);assertEquals(-2,r.parameters["delta"])
  }
  assertNotNull(apply("صدا رو دو پله آروم تر و زیاد کن","ADJUST_AUDIO_VOLUME","delta",2,listOf(-2,2)).error)
  assertNotNull(apply("دما رو دو درجه آروم تر کن","ADJUST_CABIN_TEMPERATURE","delta",2,listOf(-2,2)).error)
 }
 @Test fun notMoreIsNotNine() {
  for(t in listOf("بلندی آهنگ دقیقا چهار باشه نه بیشتر","صدا دقیقا چهار باشه نه کمتر")) {
   val r=apply(t,"SET_AUDIO_VOLUME","level",9,listOf(4,9));assertNull(r.error);assertEquals(4,r.parameters["level"])
  }
  val nine=apply("صدا نه درجه باشه","SET_AUDIO_VOLUME","level",4,listOf(4,9));assertNull(nine.error);assertEquals(9,nine.parameters["level"])
  assertNotNull(apply("صدا چهار یا نه باشه","SET_AUDIO_VOLUME","level",4,listOf(4,9)).error)
 }
}
