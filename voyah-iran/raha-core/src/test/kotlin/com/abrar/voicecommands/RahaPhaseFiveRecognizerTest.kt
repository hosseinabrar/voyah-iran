package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaPhaseFiveRecognizerTest {
 data class Case(val phrase:String,val action:String,val params:Map<String,Any>)
 @Test fun lightingCorpus() {
 val parser=RahaCommandRecognizer()
 val cases=listOf(
Case("نوربالای خودکار ","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نوربالای خودکار رو روشن کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نوربالای خودکار رو اکتیو کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نوربالای خودکار رو خاموش کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to false)),
Case("نوربالای خودکار رو دی اکتیو کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to false)),
Case("نور بالای اتوماتیک ","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نور بالای اتوماتیک رو روشن کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نور بالای اتوماتیک رو اکتیو کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
Case("نور بالای اتوماتیک رو خاموش کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to false)),
Case("نور بالای اتوماتیک رو دی اکتیو کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to false)),
Case("چراغ روز ","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("چراغ روز رو روشن کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("چراغ روز رو اکتیو کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("چراغ روز رو خاموش کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to false)),
Case("چراغ روز رو دی اکتیو کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to false)),
Case("دی لایت ","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("دی لایت رو روشن کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("دی لایت رو اکتیو کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to true)),
Case("دی لایت رو خاموش کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to false)),
Case("دی لایت رو دی اکتیو کن","SET_DAYTIME_RUNNING_LIGHTS",mapOf("enabled" to false)),
Case("مه شکن ","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن رو روشن کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن رو اکتیو کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن رو خاموش کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to false)),
Case("مه شکن رو دی اکتیو کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to false)),
Case("مه شکن عقب ","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن عقب رو روشن کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن عقب رو اکتیو کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
Case("مه شکن عقب رو خاموش کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to false)),
Case("مه شکن عقب رو دی اکتیو کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to false)),
Case("برف پاک کن خودکار ","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن خودکار رو روشن کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن خودکار رو اکتیو کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن خودکار رو خاموش کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to false)),
Case("برف پاک کن خودکار رو دی اکتیو کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to false)),
Case("برف پاک کن اتوماتیک ","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن اتوماتیک رو روشن کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن اتوماتیک رو اکتیو کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to true)),
Case("برف پاک کن اتوماتیک رو خاموش کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to false)),
Case("برف پاک کن اتوماتیک رو دی اکتیو کن","SET_RAIN_SENSING_WIPERS",mapOf("enabled" to false)),
Case("چراغ جلو رو خاموش کن","SET_HEADLIGHT_MODE",mapOf("mode" to "off")),
Case("چراغ جلو رو اتوماتیک کن","SET_HEADLIGHT_MODE",mapOf("mode" to "auto")),
Case("چراغ جلو رو روشن کن","SET_HEADLIGHT_MODE",mapOf("mode" to "lowBeam")),
Case("نور پایین","SET_HEADLIGHT_MODE",mapOf("mode" to "lowBeam")),
Case("چراغ کوچک رو روشن کن","SET_HEADLIGHT_MODE",mapOf("mode" to "position")),
Case("چراغ های جلو رو خودکار کن","SET_HEADLIGHT_MODE",mapOf("mode" to "auto")),
Case("نورپردازی داخلی رو عادی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "normal")),
Case("نورپردازی داخلی رو موزیک کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نورپردازی داخلی رو هماهنگ با موسیقی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نورپردازی داخلی رو حالت رانندگی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "driveMode")),
Case("نورپردازی داخلی رو حداکثر کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نورپردازی داخلی رو حداقل کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "min")),
Case("نورپردازی داخلی رو خیلی زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نورپردازی داخلی رو تا ته زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نورپردازی داخلی رو 0 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 0)),
Case("نورپردازی داخلی رو 30 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 30)),
Case("نورپردازی داخلی رو 50 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 50)),
Case("نورپردازی داخلی رو 100 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 100)),
Case("نورپردازی داخلی رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نورپردازی داخلی رو کم کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نورپردازی داخلی رو بیشتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نورپردازی داخلی رو کمتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور داخل رو عادی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "normal")),
Case("نور داخل رو موزیک کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور داخل رو هماهنگ با موسیقی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور داخل رو حالت رانندگی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "driveMode")),
Case("نور داخل رو حداکثر کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور داخل رو حداقل کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "min")),
Case("نور داخل رو خیلی زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور داخل رو تا ته زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور داخل رو 0 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 0)),
Case("نور داخل رو 30 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 30)),
Case("نور داخل رو 50 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 50)),
Case("نور داخل رو 100 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 100)),
Case("نور داخل رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور داخل رو کم کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور داخل رو بیشتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور داخل رو کمتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور کابین رو عادی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "normal")),
Case("نور کابین رو موزیک کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور کابین رو هماهنگ با موسیقی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور کابین رو حالت رانندگی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "driveMode")),
Case("نور کابین رو حداکثر کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور کابین رو حداقل کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "min")),
Case("نور کابین رو خیلی زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور کابین رو تا ته زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور کابین رو 0 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 0)),
Case("نور کابین رو 30 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 30)),
Case("نور کابین رو 50 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 50)),
Case("نور کابین رو 100 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 100)),
Case("نور کابین رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور کابین رو کم کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور کابین رو بیشتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور کابین رو کمتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور محیطی رو عادی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "normal")),
Case("نور محیطی رو موزیک کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور محیطی رو هماهنگ با موسیقی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
Case("نور محیطی رو حالت رانندگی کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "driveMode")),
Case("نور محیطی رو حداکثر کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور محیطی رو حداقل کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "min")),
Case("نور محیطی رو خیلی زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور محیطی رو تا ته زیاد کن","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("level" to "max")),
Case("نور محیطی رو 0 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 0)),
Case("نور محیطی رو 30 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 30)),
Case("نور محیطی رو 50 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 50)),
Case("نور محیطی رو 100 درصد بذار","SET_AMBIENT_LIGHT_BRIGHTNESS",mapOf("percent" to 100)),
Case("نور محیطی رو زیاد کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور محیطی رو کم کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("نور محیطی رو بیشتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to 1)),
Case("نور محیطی رو کمتر کن","ADJUST_AMBIENT_LIGHT_BRIGHTNESS",mapOf("delta" to -1)),
Case("چراغ مه شکن جلو رو روشن کن","UNKNOWN",mapOf()),
Case("نور بالا روشن کن","UNKNOWN",mapOf()),
Case("نور داخل رو 120 درصد بذار","UNKNOWN",mapOf()),
Case("برف پاک کن حالت سرویس","UNKNOWN",mapOf()),
Case("نور داخل آبی کن","UNKNOWN",mapOf())
 )
 val failures=mutableListOf<String>()
 for(c in cases) {
 val r=parser.recognize(c.phrase)
 if(c.action=="UNKNOWN") {if(r !is Recognition.Unknown) failures += "${c.phrase}: $r"}
 else if(r !is Recognition.Matched || r.command.action!=c.action || r.command.parameters!=c.params) failures += "${c.phrase}: expected ${c.action} ${c.params}; actual $r"
 }
 assertTrue(failures.joinToString("\n"),failures.isEmpty())
 println("Phase 5: ${cases.size} examples passed")
 }

 @Test fun reportedLightingPhrases() {
 val parser=RahaCommandRecognizer()
 val cases=listOf(
 Case("چراغ ها رو به حالت اتومات در بیار","SET_HEADLIGHT_MODE",mapOf("mode" to "auto")),
 Case("نوربالا اتوماتیک","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
 Case("نوربالا خودکار","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
 Case("نوربالا رو خودکار کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to true)),
 Case("نورپردازی داخلی رو روشن کن","SET_AMBIENT_LIGHT_POWER",mapOf("enabled" to true)),
 Case("نورپردازی موزیکال رو فعال کن","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music","enabled" to true)),
 Case("نورپردازی با آهنگ","SET_AMBIENT_LIGHT_MODE",mapOf("mode" to "music")),
 Case("امبینت لایت رو روشن کن","SET_AMBIENT_LIGHT_POWER",mapOf("enabled" to true)),
 Case("ام بینت لایت رو دی اکتیو کن","SET_AMBIENT_LIGHT_POWER",mapOf("enabled" to false)),
 Case("فاگ لایت","SET_REAR_FOG_LIGHT",mapOf("enabled" to true)),
 Case("فاگ لایت رو خاموش کن","SET_REAR_FOG_LIGHT",mapOf("enabled" to false)),
 Case("نور پایین رو خاموش کن","SET_HEADLIGHT_MODE",mapOf("mode" to "position")),
 Case("نوربالا رو خودکار دی اکتیو کن","SET_AUTO_HIGH_BEAM",mapOf("enabled" to false))
 )
 for(c in cases) {
 val result=parser.recognize(c.phrase)
 assertTrue("${c.phrase}: $result",result is Recognition.Matched)
 val cmd=(result as Recognition.Matched).command
 assertEquals(c.phrase,c.action,cmd.action)
 assertEquals(c.phrase,c.params,cmd.parameters)
 }
 }

 @Test fun wakeGateIsolationAndTimeout() {
  val g=com.abrar.raha.RahaWakeGate(setOf("هی رها","سلام رها"))
  assertNull(g.partial("کولر روشن",0)); assertFalse(g.active)
  assertNull(g.final("رها کولر روشن",1)); assertFalse(g.active)
  assertEquals("کولر",g.partial("هی رها کولر",10));assertTrue(g.active)
  assertEquals("کولر روشن",g.final("هی رها کولر روشن",20));assertFalse(g.active)
  assertNull(g.final("کولر خاموش",30))
  assertNull(g.final("سلام رها",40));assertTrue(g.active)
  assertEquals("اکو",g.final("اکو",100));assertFalse(g.active)
  g.partial("هی رها",200); assertTrue(g.expire(8200));assertFalse(g.active)
  assertNull(g.final("اسپورت",8201))
  val name=com.abrar.raha.RahaWakeGate(setOf("رها"))
  assertEquals("کامفورت",name.final("رها کامفورت",0))
  assertNull(name.final("رهام کامفورت",1))
  val longest=com.abrar.raha.RahaWakeGate(setOf("رها","هی رها"))
  assertEquals("اکو",longest.final("هی رها اکو",0))
  g.partial("هی رها",9000);assertNull(g.final("هی راه",9010));assertFalse(g.active)
 }
}
