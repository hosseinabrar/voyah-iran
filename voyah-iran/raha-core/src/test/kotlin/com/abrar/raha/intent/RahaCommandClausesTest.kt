package com.abrar.raha.intent
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
class RahaCommandClausesTest {
 @Test fun courtesyPreservesRealConditionsAndProhibitions() {
  for(t in listOf("اگه ممکنه کولر رو روشن کن","رها اگر می توانی کولر رو روشن کن","اگه میتونی صندوق رو باز کن"))assertFalse(t,RahaCommandClauses.blocked(t))
  for(t in listOf("اگر بارون اومد شیشه رو ببند","اگه ممکنه اگر بارون اومد شیشه رو ببند","چرا کولر رو روشن کنم","اگه ممکنه کولر رو روشن کن؟","کولر رو روشن نکن","شیشه رو نبند"))assertTrue(t,RahaCommandClauses.blocked(t))
 }
 @Test fun correctionKeepsOnlyExplicitIntendedState() {
  for((t,b) in listOf("کولر رو از حالت فعال دربیار" to false,"کولر رو از حالت غیر فعال خارج کن" to true,"کولر رو روشن نکن فقط خاموشش کن" to false,"کولر رو فعال نکن در عوض غیرفعالش کن" to false,"کولر رو خاموش نکن فقط فعالش کن" to true,"کولر رو از خاموش به روشن تغییر بده" to true,"کولر رو از حالت روشن به حالت خاموش ببر" to false)) {
   assertFalse(t,RahaCommandClauses.blocked(t))
   assertNull(t,RahaIntentGrounding.consistency(t,"SET_AC_POWER",mapOf("enabled" to b)))
   assertNotNull(t,RahaIntentGrounding.consistency(t,"SET_AC_POWER",mapOf("enabled" to !b)))
  }
  for(t in listOf("کولر رو هم روشن هم خاموش کن","کولر رو روشن نکن فقط خاموشش کن و روشنش کن","کولر رو از خاموش به روشن تغییر بده ولی خاموش کن"))for(b in listOf(true,false))assertNotNull(t,RahaIntentGrounding.consistency(t,"SET_AC_POWER",mapOf("enabled" to b)))
 }
 @Test fun allReported320Cases() {
  val rows=JSONArray(checkNotNull(javaClass.getResourceAsStream("/intent-clause-320.json")).bufferedReader().use { it.readText() });assertEquals(320,rows.length())
  for(i in 0 until rows.length()) {
   val r=rows.getJSONObject(i);val p=r.getJSONObject("parameters");val params=p.keys().asSequence().associateWith { p.get(it) };val text=r.getString("text")
   assertFalse(text,RahaCommandClauses.blocked(text))
   val allowed=params.mapValues { listOf(it.value) }
   assertNull(text,RahaIntentGrounding.apply(text,r.getString("action"),params,allowed).error)
  }
 }
}
