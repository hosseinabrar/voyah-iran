package com.abrar.raha.intent
import org.junit.Test
import org.junit.Assert.*
class RahaVerblessGuardTest {
 @Test fun shortEndpointsStayConsistent() {
  for((text,value) in listOf("پرده باز" to 100,"پرده بسته" to 0,"رها پرده سقف بسته" to 0,"لطفا سایبان کاملا باز" to 100,"پرده سانروف رو کامل بسته" to 0)) {
   assertNull(text,RahaIntentGrounding.consistency(text,"SET_SUNSHADE_POSITION",mapOf("position" to value)))
   assertNotNull(text,RahaIntentGrounding.consistency(text,"SET_SUNSHADE_POSITION",mapOf("position" to 100-value)))
  }
  for((text,value) in listOf("پرده نصفه باز" to 50,"پرده بیست درصد باز" to 20,"پرده کمی بازتر" to 60))assertNull(text,RahaIntentGrounding.consistency(text,"SET_SUNSHADE_POSITION",mapOf("position" to value)))
  assertNotNull(RahaIntentGrounding.consistency("پرده باز نکن","SET_SUNSHADE_POSITION",mapOf("position" to 100)))
 }
}
