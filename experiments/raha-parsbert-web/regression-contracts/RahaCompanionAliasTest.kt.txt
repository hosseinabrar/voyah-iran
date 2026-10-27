package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaCompanionAliasTest {
 @Test fun passengerAliases() {
  val parser=RahaCommandRecognizer()
  for (text in listOf("شیشه شاگرد رو بده پایین", "گرمکن صندلی شاگرد رو روشن کن", "دمای شاگرد رو روی بیست و سه بذار", "ماساژ صندلی شاگرد رو روشن کن", "تهویه صندلی شاگرد رو روشن کن", "نور نمایشگر شاگرد رو زیاد کن", "آینه شاگرد رو جمع کن", "شیشه پشت شاگرد رو باز کن")) {
   val expected=parser.recognize(text)
   val actual=parser.recognize(text.replace("شاگرد","همراه"))
   assertTrue("$text => $expected",expected is Recognition.Matched)
   assertTrue("$text => $actual",actual is Recognition.Matched)
   assertEquals((expected as Recognition.Matched).command.action,(actual as Recognition.Matched).command.action)
   assertEquals(text,expected.command.parameters,actual.command.parameters)
  }
 }
 @Test fun passengerMemoryRejected() {
  for(text in listOf("وضعیت صندلی همراه رو ذخیره کن","وضعیت صندلی همراه رو بازیابی کن"))
   assertTrue(text,RahaCommandRecognizer().recognize(text) is Recognition.Unknown)
 }
 @Test fun coDriverCompoundNeverTargetsDriver() {
  val parser=RahaCommandRecognizer()
  for(alias in listOf("کمک راننده","کمک‌راننده")) {
   for(text in listOf("شیشه شاگرد رو بده پایین","شیشه عقب سمت شاگرد رو باز کن","گرمکن صندلی شاگرد رو روشن کن","دمای شاگرد رو روی بیست و سه بذار","آینه شاگرد رو جمع کن","نور نمایشگر شاگرد رو زیاد کن")) {
    val expected=parser.recognize(text) as Recognition.Matched
    val actual=parser.recognize(text.replace("شاگرد",alias))
    assertTrue("$alias $text => $actual",actual is Recognition.Matched)
    assertEquals(expected.command.action,(actual as Recognition.Matched).command.action)
    assertEquals(expected.command.parameters,actual.command.parameters)
   }
   assertTrue(parser.recognize("وضعیت صندلی $alias رو ذخیره کن") is Recognition.Unknown)
   assertTrue(parser.recognize("وضعیت صندلی $alias رو بازیابی کن") is Recognition.Unknown)
  }
 }
}
