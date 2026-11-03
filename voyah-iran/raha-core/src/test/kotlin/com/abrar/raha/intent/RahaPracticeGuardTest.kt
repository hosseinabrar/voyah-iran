package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
class RahaPracticeGuardTest {
 private fun delta(text:String,temperature:Boolean=false):RahaIntentGrounding.Result {
  val action=if(temperature) "ADJUST_CABIN_TEMPERATURE" else "ADJUST_AUDIO_VOLUME"
  val extra=if(temperature) "zone" to "front" else "channel" to "media"
  return RahaIntentGrounding.apply(text,action,mapOf(extra,"delta" to -1),mapOf("delta" to (-16..16).filter { it!=0 }))
 }
 @Test fun explicitComparativesAndObjectSuffixes() {
  for((text,value) in listOf("صدا رو دو پله کمش کن" to -2,"صدا رو دو پله زیادش کن" to 2,"صدا رو دو پله بالاتر ببر" to 2,"صدا دو پله پایین تر باشه" to -2,"به صدا دو پله اضافه کن" to 2,"صدا رو دو پله بلندترش کن" to 2,"آهنگ رو کم می شنوم یک درجه بلندترش کن" to 1)) {
   val r=delta(text);assertNull(text,r.error);assertEquals(text,value,r.parameters["delta"])
  }
  for((word,value) in listOf("گرم ترش" to 3,"سردترش" to -3,"خنک ترش" to -3)) {
   val r=delta("اتاق رو سه درجه $word کن",true);assertNull(r.error);assertEquals(value,r.parameters["delta"])
  }
 }
 @Test fun conflictingAndUnknownDirectionsReject() {
  for(t in listOf("صدا رو دو پله کم و زیاد کن","صدا رو دو پله تغییر بده","صدا رو منفی دو پله زیاد کن","صدا رو دو یا سه پله زیاد کن"))assertNotNull(t,delta(t).error)
 }
 @Test fun negativeStateDoesNotContradictMatchingCommand() {
  for(t in listOf("سردکن روشن نمونه خاموشش کن","گرمکن روشن نباشه خاموش کن")) {
   assertNull(RahaIntentGrounding.consistency(t,"SET_SEAT_HEATING",mapOf("enabled" to false)))
   assertNotNull(RahaIntentGrounding.consistency(t,"SET_SEAT_HEATING",mapOf("enabled" to true)))
  }
  for(t in listOf("گرمکن هم روشن هم خاموش باشه","گرمکن روشن نمونه ولی روشن کن"))for(b in listOf(false,true))assertNotNull(RahaIntentGrounding.consistency(t,"SET_SEAT_HEATING",mapOf("enabled" to b)))
 }
}
