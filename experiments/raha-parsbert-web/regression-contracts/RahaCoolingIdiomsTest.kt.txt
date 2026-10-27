package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaCoolingIdiomsTest {
 @Test fun colloquialCoolingUsesExistingCoolingConstraints() {
  val parser=RahaCommandRecognizer()
  val expected=(parser.recognize("کولر رو روشن کن") as Recognition.Matched).command
  for(verb in listOf("بگیر","بگیرین","بگیرید","بگیرم","بگیریم","بگیری","بگیره","گرفتن","بزن","بزنین","بزنید","بزنیم","بزنی","بزنه"))
   for(pattern in listOf("کولر %s","کولر رو %s","لطفا کولر %s","کولر را %s")) {
    val phrase=pattern.format(verb)
    val result=parser.recognize(phrase)
    assertTrue("$phrase: $result",result is Recognition.Matched)
    val command=(result as Recognition.Matched).command
    assertEquals(phrase,expected.action,command.action)
    assertEquals(phrase,expected.parameters,command.parameters)
   }
 }
}
