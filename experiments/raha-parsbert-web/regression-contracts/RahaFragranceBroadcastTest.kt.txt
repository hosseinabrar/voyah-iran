package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaFragranceBroadcastTest {
 @Test fun broadcastWithOrWithoutNamedScent() {
  val parser=RahaCommandRecognizer()
  val nouns=mapOf("عطر" to 0,"رایحه" to 0,"بو" to 0,"خوشبوکننده" to 0,"بوی گل" to 2,"عطر گل" to 2,"بوی اسکارلت" to 2,"بوی بامبو" to 1,"بوی مه" to 3)
  for((noun,channel) in nouns) for(template in listOf("%s پخش کن","%s رو پخش کن","لطفا %s پخش بکن","%s را پخش کنید","%s رو توی ماشین پخش کن","پخش کن %s رو")) {
   val phrase=template.format(noun)
   val result=parser.recognize(phrase)
   assertTrue("$phrase: $result",result is Recognition.Matched)
   val command=(result as Recognition.Matched).command
   assertEquals(phrase,if(channel==0)"SET_FRAGRANCE_POWER" else "CONFIGURE_FRAGRANCE",command.action)
   val expected=if(channel==0)mapOf("enabled" to true) else mapOf("enabled" to true,"channel" to channel)
   assertEquals(phrase,expected,command.parameters)
  }
 }
}
