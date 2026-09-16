package com.abrar.raha
import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Lighting and rain sensing intents. No vehicle writes. */
internal object RahaPhaseFiveRecognizer {
 val head="چراغ جلو|چراغ های جلو|چراغ|چراغ ها|نور چراغ جلو"
 val ambient="نورپردازی داخلی|نور پردازی داخلی|نورپردازی داخل|نور داخل|نور کابین|نور محیطی|نورپردازی|امبینت|آمبینت|ام بینت|آم بینت|امبینت لایت|آمبینت لایت|ام بینت لایت|آم بینت لایت"
 val automatic="خودکار|اتوماتیک|اتومات|اوتو|اتو"
 val toggles=linkedMapOf(
  RahaAction.SET_AUTO_HIGH_BEAM to "نوربالای خودکار|نور بالای خودکار|نوربالای اتوماتیک|نور بالای اتوماتیک|نور بالا اتومات|نوربالا اتو",
  RahaAction.SET_DAYTIME_RUNNING_LIGHTS to "چراغ روشنایی روز|چراغ روز|دی لایت|دیلایت|دی آر ال",
  RahaAction.SET_REAR_FOG_LIGHT to "مه شکن عقب|مه شکن|مهشکن عقب|مهشکن|فاگ لایت|فاگ لایت عقب",
  RahaAction.SET_RAIN_SENSING_WIPERS to "برف پاک کن خودکار باران|برف پاک کن خودکار|برف پاک کن اتوماتیک|برف پاک کن اتومات|برف پاک کن اتو"
 )
 val vocabulary=listOf(head,ambient,automatic,"نور پایین|چراغ کوچک|چراغ های کوچک|چراغ پارک|سکن|نوربالا|نور بالا|موزیک|موسیقی|هماهنگ با موسیقی|عادی|نرمال|حالت رانندگی|شدت|روشنایی|درصد|آهنگ|موزیکال|در بیار")+toggles.values
 fun recognize(text:String):Recognition.Matched? {
  val found=mutableListOf<VehicleCommand>()
  fun rule(action:RahaAction, body:RahaCommandRecognizer.Scan.()->Map<String,Any>?) {
   var local=text
   for((word,n) in mapOf("صد" to "100","پنجاه" to "50","چهل" to "40","شصت" to "60","هفتاد" to "70","هشتاد" to "80","نود" to "90"))
    local=local.split(' ').joinToString(" ") {if(it==word)n else it}
   val scan=RahaCommandRecognizer.Scan(local)
   val p=scan.body() ?: return
   if(scan.finished()) found+=VehicleCommand(action.name,action.name,p,describe(action,p))
  }
  for((a,n) in toggles) rule(a) {
   if(!take(n)) return@rule null
   mapOf("enabled" to (power() ?: true))
  }
  rule(RahaAction.SET_AUTO_HIGH_BEAM) {
   if(!take("نوربالا|نور بالا")) return@rule null
   if(!take(automatic)) return@rule null
   mapOf("enabled" to (power() ?: true))
  }
  rule(RahaAction.SET_AMBIENT_LIGHT_POWER) {
   if(!take(ambient)) return@rule null
   val on=power() ?: return@rule null
   mapOf("enabled" to on)
  }
  rule(RahaAction.SET_HEADLIGHT_MODE) {
   val low=take("نور پایین")
   val position=take("چراغ کوچک|چراغ های کوچک|چراغ پارک|سکن")
   val noun=take(head)
   if(!low && !position && !noun) return@rule null
   val auto=take(automatic)
   if(auto) take("در بیار|دربیار")
   val on=power()
   if(listOf(low,position,auto).count{it}>1) return@rule null
   val mode=when {on==false && (auto || low)->"position";on==false->"off";auto->"auto";position->"position";low || on==true->"lowBeam";else->return@rule null}
   mapOf("mode" to mode)
  }
  rule(RahaAction.SET_AMBIENT_LIGHT_MODE) {
   if(!take(ambient)) return@rule null
   val mode=when {
    take("هماهنگ با موسیقی|هماهنگ با موزیک|موسیقی|موزیک|موزیکال|آهنگ")->"music"
    take("هماهنگ با حالت رانندگی|حالت رانندگی|مود رانندگی|درایو مود")->"driveMode"
    take("عادی|معمولی|نرمال|ثابت")->"normal"
    else->return@rule null
   }
   take("با"); take("هماهنگ")
   val enabled=power()
   if(enabled==null) mapOf("mode" to mode) else mapOf("mode" to mode,"enabled" to enabled)
  }
  rule(RahaAction.SET_AMBIENT_LIGHT_BRIGHTNESS) {
   if(!take(ambient)) return@rule null
   take("روشنایی")
   levelUnits()
   val bound=levelBound()
   if(bound!=null) take("زیاد|کم|بیشتر|کمتر")
   val percent=take("درصد")
   val n=number()
   when {
    bound!=null && n==null && !percent->mapOf("level" to bound)
    bound==null && n!=null && n in 0.0..100.0 && n%1==0.0 ->mapOf("percent" to n.toInt())
    else->null
   }
  }
  rule(RahaAction.ADJUST_AMBIENT_LIGHT_BRIGHTNESS) {
   if(!take(ambient)) return@rule null
   take("روشنایی");levelUnits()
   if(levelBound()!=null)return@rule null
   val delta=direction() ?: return@rule null
   mapOf("delta" to delta)
  }
  return found.distinctBy{it.action to it.parameters}.singleOrNull()?.let{Recognition.Matched(it)}
 }
 private fun describe(action:RahaAction,p:Map<String,Any>):String {
  val names=mapOf(RahaAction.SET_AMBIENT_LIGHT_POWER to "نورپردازی داخلی",RahaAction.SET_HEADLIGHT_MODE to "حالت چراغ جلو",RahaAction.SET_AUTO_HIGH_BEAM to "نوربالای خودکار",
   RahaAction.SET_DAYTIME_RUNNING_LIGHTS to "چراغ روز",RahaAction.SET_REAR_FOG_LIGHT to "مه‌شکن عقب",
   RahaAction.SET_RAIN_SENSING_WIPERS to "برف‌پاک‌کن خودکار باران",RahaAction.SET_AMBIENT_LIGHT_MODE to "حالت نورپردازی داخلی",
   RahaAction.SET_AMBIENT_LIGHT_BRIGHTNESS to "شدت نورپردازی داخلی",RahaAction.ADJUST_AMBIENT_LIGHT_BRIGHTNESS to "تغییر شدت نورپردازی داخلی")
  val labels=mapOf("off" to "خاموش","auto" to "خودکار","lowBeam" to "نور پایین","position" to "چراغ کوچک",
   "normal" to "عادی","music" to "هماهنگ با موسیقی","driveMode" to "هماهنگ با حالت رانندگی","min" to "حداقل","max" to "حداکثر")
  return names[action]+": "+p.map{(k,v)->when(k){
   "enabled"->if(v==true)"فعال" else "غیرفعال"
   "percent"->"$v درصد"
   "delta"->if(v==1)"یک پله بیشتر" else "یک پله کمتر"
   else->labels[v] ?: v.toString()
  }}.joinToString("، ")
 }
}

