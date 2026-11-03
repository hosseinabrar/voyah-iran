package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
class RahaHundredDurationTest {
 @Test fun hundredsComposeButSeparateTensDoNot() {
  for((words,n) in listOf("صد و بیست" to 120,"صد و پنجاه" to 150,"صد و هشتاد" to 180)) {
   val r=RahaIntentGrounding.apply("زمان حفظ برق رو $words دقیقه کن","SET_POWER_KEEP_DURATION",mapOf("minutes" to 30),mapOf("minutes" to listOf(30,60,90,120,150,180,-1)))
   assertNull(r.error);assertEquals(n,r.parameters["minutes"])
  }
  assertEquals(2,RahaIntentGrounding.quantities("سی و شصت دقیقه").size)
  assertEquals(125.0,RahaIntentGrounding.quantities("صد و بیست و پنج دقیقه").single().value,0.0)
 }
}
