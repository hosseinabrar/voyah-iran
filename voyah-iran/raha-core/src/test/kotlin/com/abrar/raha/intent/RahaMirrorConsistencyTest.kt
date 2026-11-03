package com.abrar.raha.intent
import org.junit.Test
import org.junit.Assert.*
class RahaMirrorConsistencyTest {
 @Test fun explicitDirectionChecksWithoutReplacingPrediction() {
  for((text,fold) in listOf("آینه رو باز کن" to false,"آینه همراه باز بشه" to false,"آینه برای دیدن عقب باز باشه" to false,"آینه رو جمع کن" to true,"آینه راننده تا بشه" to true,"آینه رو باز کن جمع نشه" to false)) {
   assertNull(text,RahaIntentGrounding.consistency(text,"SET_MIRROR_FOLD",mapOf("folded" to fold)))
   assertNotNull(text,RahaIntentGrounding.consistency(text,"SET_MIRROR_FOLD",mapOf("folded" to !fold)))
  }
  for(fold in listOf(true,false))assertNotNull(RahaIntentGrounding.consistency("آینه رو باز کن و جمع کن","SET_MIRROR_FOLD",mapOf("folded" to fold)))
  assertNull(RahaIntentGrounding.consistency("آینه جمع شده رو بازش کن","SET_MIRROR_FOLD",mapOf("folded" to false)))
  assertNull(RahaIntentGrounding.consistency("آینه باز نمونه جمعش کن","SET_MIRROR_FOLD",mapOf("folded" to true)))
 }
}
