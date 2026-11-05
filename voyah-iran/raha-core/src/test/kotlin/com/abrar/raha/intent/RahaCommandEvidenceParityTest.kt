package com.abrar.raha.intent
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
class RahaCommandEvidenceParityTest {
 @Test fun sharedFixturesAgreeWithPython() {
  val rows=JSONArray(javaClass.getResource("/command-evidence-parity.json")!!.readText())
  for(i in 0 until rows.length()) {
   val r=rows.getJSONObject(i);val p=r.getJSONObject("parameters");val params=p.keys().asSequence().associateWith{p.get(it)}
   assertEquals(r.getString("text"),r.getBoolean("allowed"),RahaCommandEvidence.check(r.getString("text"),r.getString("action"),params)==null)
  }
 }
 @Test fun rejectsWrongTargets() {
  for(t in listOf("سانروف رو گرم کن","پیتزا رو گرم کن","بزن بریم گرم"))assertNotNull(RahaCommandEvidence.check(t,"SET_SEAT_HEATING",mapOf("enabled" to true,"seat" to "driver")))
 }
 @Test fun colloquialSmallQuantityIsNotOnePercent() {
  assertNull(RahaIntentGrounding.consistency("یک کم شیشه رو بده پایین","ADJUST_WINDOW_POSITION",mapOf("deltaPercent" to -10,"window" to "driver")))
 }
}
