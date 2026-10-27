package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaFragranceOrderTest {
 @Test fun scentNamesAndWordOrderPreserveChannelAndActivation() {
  val parser=RahaCommandRecognizer()
  val names=mapOf(
   1 to listOf("فرش بامبو","بامبو فرش","بامبو فریش","بامبو تازه","تازه بامبو","بامبو"),
   2 to listOf("اسکارلت","پیونی اسکارلت","اسکارلت پیونی","اسکارلت پی اونی","پی اونی اسکارلت","اسکارلت پونی"),
   3 to listOf("واتری میست","میست واتری","میست واتر ی","میست","مه آبی","آبی مه")
  )
  var count=0
  for((channel,aliases) in names) for(name in aliases)
  for(noun in listOf("رایحه","عطر","خوشبوکننده","عطر پاش")) {
   val phrases=listOf("$noun $name رو فعال کن","$name $noun رو فعال کن",
     "فعال کن $noun $name","$noun رو فعال کن $name",
     "$noun $name را روشن نمایید","$name را برای $noun فعال کنید")
   for(text in phrases) {
    val result=parser.recognize(text)
    assertTrue("$text -> $result",result is Recognition.Matched)
    val command=(result as Recognition.Matched).command
    assertEquals(text,"CONFIGURE_FRAGRANCE",command.action)
    assertEquals(text,mapOf("channel" to channel,"enabled" to true),command.parameters)
    count++
   }
   val text="$noun $name را برای سی دقیقه با حداکثر شدت فعال کنید"
   val result=parser.recognize(text)
   assertTrue("$text -> $result",result is Recognition.Matched)
   assertEquals(mapOf("channel" to channel,"enabled" to true,"durationMinutes" to 30,"intensity" to "high"),
     (result as Recognition.Matched).command.parameters)
   count++
  }
  println("Fragrance order: $count variants passed")
 }
}
