package com.abrar.voicecommands
import com.abrar.raha.*
import org.junit.Assert.*
import org.junit.Test
class RahaDirectionFragranceTest {
 private val parser=RahaCommandRecognizer()
 private fun equalIntent(a:String,b:String) {
  val x=parser.recognize(a); val y=parser.recognize(b)
  assertTrue("$a => $x",x is Recognition.Matched)
  assertTrue("$b => $y",y is Recognition.Matched)
  assertEquals((x as Recognition.Matched).command.action,(y as Recognition.Matched).command.action)
  assertEquals(x.command.parameters,y.command.parameters)
 }
 @Test fun directionFillersPreserveTargets() {
  for ((a,b) in listOf(
   "شیشه راننده رو بده پایین" to "شیشه جهت راننده رو بده پایین",
   "شیشه عقب راننده رو بده پایین" to "شیشه عقب سمت راننده رو بده پایین",
   "گرمکن صندلی شاگرد رو فعال کن" to "گرمکن صندلی سمت شاگرد رو فعال کن",
   "شیشه عقب همراه رو باز کن" to "شیشه عقب جهت همراه رو باز کن",
   "جهت باد رو بده پایین" to "باد رو بده پایین")) equalIntent(a,b)
 }
 @Test fun fragranceSynonymsWithAndWithoutScent() {
  for(noun in listOf("خوشبوکننده","خوشبو کننده","خوش‌بوکننده","خوش بو کننده","رایحه","عطر","عطرپاش","عطر پاش","بوگیر","بو گیر")) {
   equalIntent("رایحه رو فعال کن","$noun رو فعال کن")
   for(scent in listOf("بامبو","گل","مه")) {
    equalIntent("رایحه $scent رو فعال کن","$noun $scent رو فعال کن")
    equalIntent("رایحه $scent رو پخش کن","$scent $noun رو پخش کن")
   }
  }
 }
 @Test fun decodingIncludesNamedFragrancePhrases() {
  val phrases=RahaSpeechVocabulary.phrases()
  assertTrue(phrases.contains("خوشبوکننده بامبو رو فعال کن"))
  assertTrue(phrases.contains("بوگیر بامبو رو فعال کن"))
 }
}
