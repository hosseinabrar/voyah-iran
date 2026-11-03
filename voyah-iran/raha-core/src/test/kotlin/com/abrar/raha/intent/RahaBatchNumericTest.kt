package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
class RahaBatchNumericTest {
 @Test fun positionsPreserveClosingAndSteps() {
  for(action in listOf("ADJUST_WINDOW_POSITION","ADJUST_SUNROOF_POSITION","ADJUST_SUNSHADE_POSITION")) {
   for((text,delta) in listOf("سانروف رو ده درصد بیشتر بسته کن" to -10,"سانروف رو ده درصد بیشتر باز کن" to 10,"سانروف رو یک پله بسته کن" to -10,"سانروف رو یک پله باز کن" to 10)) {
    val r=RahaIntentGrounding.apply(text,action,mapOf("deltaPercent" to delta*9),mapOf("deltaPercent" to listOf(-10,10)))
    assertNull(r.error);assertEquals(delta,r.parameters["deltaPercent"])
   }
   assertNotNull(RahaIntentGrounding.apply("سانروف رو ده درصد باز و بسته کن",action,mapOf("deltaPercent" to 10),mapOf("deltaPercent" to listOf(-10,10))).error)
  }
 }
 @Test fun fragranceDurationIsIndependentOfNamedIntensity() {
  val p=mapOf("channel" to 1,"durationMinutes" to 60,"enabled" to true,"intensity" to "high")
  val r=RahaIntentGrounding.apply("رایحه بامبو رو نیم ساعت با شدت زیاد روشن کن","CONFIGURE_FRAGRANCE",p,mapOf("durationMinutes" to listOf(30,60)))
  assertNull(r.error);assertEquals(30,r.parameters["durationMinutes"]);assertEquals("high",r.parameters["intensity"])
 }
}
