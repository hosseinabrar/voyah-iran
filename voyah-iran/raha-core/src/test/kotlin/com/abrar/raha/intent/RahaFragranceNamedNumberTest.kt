package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
class RahaFragranceNamedNumberTest {
 @Test fun flowerNameIsNotAChannelNumber() {
  for(name in listOf("گل صد تومنی","صدتومنی","گل صد تومانی")) {
   assertNull(RahaIntentGrounding.consistency("عطر $name رو انتخاب کن","SET_FRAGRANCE_CHANNEL",mapOf("channel" to 2)))
  }
  assertNotNull(RahaIntentGrounding.consistency("عطر گل صد تومنی شماره سه رو انتخاب کن","SET_FRAGRANCE_CHANNEL",mapOf("channel" to 2)))
 }
 @Test fun durationInNamedFragranceCommandRemainsGrounded() {
  val params=mapOf("channel" to 2,"durationMinutes" to 60,"enabled" to true,"intensity" to "high")
  val r=RahaIntentGrounding.apply("گل صد تومنی رو برای سی دقیقه فعال کن","CONFIGURE_FRAGRANCE",params,mapOf("durationMinutes" to listOf(30,60)))
  assertNull(r.error);assertEquals(30,r.parameters["durationMinutes"])
 }
}
