package com.abrar.voicecommands
import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test
class RahaPhaseThreeRecognizerTest {
 data class Case(val phrase:String,val action:String,val params:Map<String,Any>)
 @Test fun phaseThreeCorpus() {
 val parser=RahaCommandRecognizer()
 val cases=listOf(
Case("لطفا شیشه  رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه  رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه  رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه  رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه  رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا شیشه  رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه همه رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه همه رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه همه رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه همه رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه همه رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا شیشه همه رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا شیشه راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا شیشه چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا شیشه شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه راست رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه راست رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا شیشه راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه عقب چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا شیشه عقب چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا شیشه عقب راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه عقب راست رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب راست رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا شیشه عقب راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا شیشه عقب شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه عقب رو باز کن","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه عقب رو بده پایین","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه عقب رو ببند","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه عقب رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه عقب رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rear","deltaPercent" to 10)),
Case("لطفا شیشه عقب رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه جلو رو باز کن","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه جلو رو بده پایین","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه جلو رو ببند","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه جلو رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه جلو رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "front","deltaPercent" to 10)),
Case("لطفا شیشه جلو رو حالت تهویه","VENT_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره  رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره  رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره  رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره  رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره  رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا پنجره  رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره همه رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره همه رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره همه رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره همه رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره همه رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا پنجره همه رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا پنجره راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا پنجره چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا پنجره شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره راست رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره راست رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا پنجره راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره عقب چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا پنجره عقب چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا پنجره عقب راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره عقب راست رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب راست رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا پنجره عقب راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا پنجره عقب شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره عقب رو باز کن","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره عقب رو بده پایین","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره عقب رو ببند","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره عقب رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره عقب رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rear","deltaPercent" to 10)),
Case("لطفا پنجره عقب رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره جلو رو باز کن","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره جلو رو بده پایین","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره جلو رو ببند","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره جلو رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره جلو رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "front","deltaPercent" to 10)),
Case("لطفا پنجره جلو رو حالت تهویه","VENT_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه ها  رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها  رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها  رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها  رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها  رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا شیشه ها  رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها همه رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها همه رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها همه رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها همه رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها همه رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا شیشه ها همه رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا شیشه ها راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا شیشه ها راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا شیشه ها چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا شیشه ها شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا شیشه ها شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها راست رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها راست رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا شیشه ها راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا شیشه ها عقب چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا شیشه ها عقب چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا شیشه ها عقب راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا شیشه ها عقب راست رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب راست رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا شیشه ها عقب راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا شیشه ها عقب شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا شیشه ها عقب رو باز کن","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه ها عقب رو بده پایین","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه ها عقب رو ببند","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه ها عقب رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه ها عقب رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rear","deltaPercent" to 10)),
Case("لطفا شیشه ها عقب رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rear")),
Case("لطفا شیشه ها جلو رو باز کن","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه ها جلو رو بده پایین","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه ها جلو رو ببند","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه ها جلو رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا شیشه ها جلو رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "front","deltaPercent" to 10)),
Case("لطفا شیشه ها جلو رو حالت تهویه","VENT_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره ها  رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها  رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها  رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها  رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها  رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا پنجره ها  رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها همه رو باز کن","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها همه رو بده پایین","OPEN_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها همه رو ببند","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها همه رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها همه رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "all","deltaPercent" to 10)),
Case("لطفا پنجره ها همه رو حالت تهویه","VENT_WINDOW",mapOf("window" to "all")),
Case("لطفا پنجره ها راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا پنجره ها راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
Case("لطفا پنجره ها چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "driver")),
Case("لطفا پنجره ها شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا پنجره ها شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها راست رو باز کن","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها راست رو ببند","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "passenger","deltaPercent" to 10)),
Case("لطفا پنجره ها راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "passenger")),
Case("لطفا پنجره ها عقب چپ رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب چپ رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب چپ رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب چپ رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب چپ رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا پنجره ها عقب چپ رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راننده رو باز کن","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راننده رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راننده رو ببند","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راننده رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راننده رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearLeft","deltaPercent" to 10)),
Case("لطفا پنجره ها عقب راننده رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearLeft")),
Case("لطفا پنجره ها عقب راست رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب راست رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب راست رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب راست رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب راست رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا پنجره ها عقب راست رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب شاگرد رو باز کن","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب شاگرد رو بده پایین","OPEN_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب شاگرد رو ببند","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب شاگرد رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب شاگرد رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rearRight","deltaPercent" to 10)),
Case("لطفا پنجره ها عقب شاگرد رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rearRight")),
Case("لطفا پنجره ها عقب رو باز کن","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره ها عقب رو بده پایین","OPEN_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره ها عقب رو ببند","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره ها عقب رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره ها عقب رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "rear","deltaPercent" to 10)),
Case("لطفا پنجره ها عقب رو حالت تهویه","VENT_WINDOW",mapOf("window" to "rear")),
Case("لطفا پنجره ها جلو رو باز کن","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره ها جلو رو بده پایین","OPEN_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره ها جلو رو ببند","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره ها جلو رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "front")),
Case("لطفا پنجره ها جلو رو یه کم باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "front","deltaPercent" to 10)),
Case("لطفا پنجره ها جلو رو حالت تهویه","VENT_WINDOW",mapOf("window" to "front")),
Case("قفل کودک  رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل کودک  رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل کودک  رو خاموش","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل کودک  رو روشن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل کودک چپ رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to true)),
Case("قفل کودک چپ رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to false)),
Case("قفل کودک چپ رو خاموش","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to false)),
Case("قفل کودک چپ رو روشن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to true)),
Case("قفل کودک راست رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to true)),
Case("قفل کودک راست رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to false)),
Case("قفل کودک راست رو خاموش","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to false)),
Case("قفل کودک راست رو روشن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to true)),
Case("قفل کودک هر دو رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل کودک هر دو رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل کودک هر دو رو خاموش","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل کودک هر دو رو روشن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل بچه  رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل بچه  رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل بچه  رو خاموش","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل بچه  رو روشن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل بچه چپ رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to true)),
Case("قفل بچه چپ رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to false)),
Case("قفل بچه چپ رو خاموش","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to false)),
Case("قفل بچه چپ رو روشن","SET_CHILD_LOCK",mapOf("side" to "left","enabled" to true)),
Case("قفل بچه راست رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to true)),
Case("قفل بچه راست رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to false)),
Case("قفل بچه راست رو خاموش","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to false)),
Case("قفل بچه راست رو روشن","SET_CHILD_LOCK",mapOf("side" to "right","enabled" to true)),
Case("قفل بچه هر دو رو فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("قفل بچه هر دو رو غیر فعال کن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل بچه هر دو رو خاموش","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to false)),
Case("قفل بچه هر دو رو روشن","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
Case("صندوق رو باز کن","OPEN_TAILGATE",mapOf()),
Case("صندوق رو ببند","CLOSE_TAILGATE",mapOf()),
Case("صندوق عقب رو باز کن","OPEN_TAILGATE",mapOf()),
Case("صندوق عقب رو ببند","CLOSE_TAILGATE",mapOf()),
Case("در صندوق رو باز کن","OPEN_TAILGATE",mapOf()),
Case("در صندوق رو ببند","CLOSE_TAILGATE",mapOf()),
Case("در شارژ رو باز کن","OPEN_CHARGE_PORT",mapOf()),
Case("درپوش شارژ رو باز کن","OPEN_CHARGE_PORT",mapOf()),
Case("پورت شارژ رو باز کن","OPEN_CHARGE_PORT",mapOf()),
Case("در باک رو باز کن","OPEN_FUEL_FLAP",mapOf()),
Case("درب باک رو باز کن","OPEN_FUEL_FLAP",mapOf()),
Case("درپوش سوخت رو باز کن","OPEN_FUEL_FLAP",mapOf()),
Case("سانروف رو باز کن","OPEN_SUNROOF",mapOf()),
Case("سانروف رو ببند","CLOSE_SUNROOF",mapOf()),
Case("سان روف رو باز کن","OPEN_SUNROOF",mapOf()),
Case("سان روف رو ببند","CLOSE_SUNROOF",mapOf()),
Case("سقف شیشه ای رو باز کن","OPEN_SUNROOF",mapOf()),
Case("سقف شیشه ای رو ببند","CLOSE_SUNROOF",mapOf()),
Case("سانروف رو نصف باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 50)),
Case("سانروف رو پنجاه درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 50)),
Case("سانروف رو ۳۰ درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 30)),
Case("سانروف رو صد درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 100)),
Case("سانروف رو صفر درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 0)),
Case("سان روف رو نصف باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 50)),
Case("سان روف رو پنجاه درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 50)),
Case("سان روف رو ۳۰ درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 30)),
Case("سان روف رو صد درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 100)),
Case("سان روف رو صفر درصد باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 0)),
Case("پرده سقف رو نصف باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 50)),
Case("پرده سقف رو پنجاه درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 50)),
Case("پرده سقف رو ۳۰ درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 30)),
Case("پرده سقف رو صد درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سقف رو صفر درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 0)),
Case("پرده سانروف رو نصف باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 50)),
Case("پرده سانروف رو پنجاه درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 50)),
Case("پرده سانروف رو ۳۰ درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 30)),
Case("پرده سانروف رو صد درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سانروف رو صفر درصد باز کن","SET_SUNSHADE_POSITION",mapOf("position" to 0)),
Case("پرده رو باز","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده رو ببند","SET_SUNSHADE_POSITION",mapOf("position" to 0)),
Case("پرده رو کنار بزن","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سقف رو باز","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سقف رو ببند","SET_SUNSHADE_POSITION",mapOf("position" to 0)),
Case("پرده سقف رو کنار بزن","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سانروف رو باز","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("پرده سانروف رو ببند","SET_SUNSHADE_POSITION",mapOf("position" to 0)),
Case("پرده سانروف رو کنار بزن","SET_SUNSHADE_POSITION",mapOf("position" to 100)),
Case("در راننده رو باز کن","SET_DOOR_LOCK",mapOf("door" to "all", "locked" to false)),
Case("کاپوت رو باز کن","UNKNOWN",mapOf()),
Case("سانروف رو 120 درصد باز کن","UNKNOWN",mapOf()),
Case("شیشه جلو رو پنجاه درصد باز کن","SET_WINDOW_POSITION",mapOf("window" to "front","percent" to 50)),
 )
 val failures=mutableListOf<String>()
 for(c in cases) {
 val r=parser.recognize(c.phrase)
 if(c.action=="UNKNOWN") { if(r !is Recognition.Unknown) failures += "${c.phrase}: $r" }
 else if(r !is Recognition.Matched || r.command.action!=c.action || r.command.parameters!=c.params) failures += "${c.phrase}: expected ${c.action} ${c.params}; actual $r"
 }
 assertTrue(failures.joinToString("\n"),failures.isEmpty())
 println("Phase 3: ${cases.size} examples passed")
 }

 @Test fun sharedOpeningAmounts() {
 val parser=RahaCommandRecognizer()
 for(noun in listOf("شیشه","پنجره","شیشه ها","پنجره ها","پرده","شید","سان شید","آفتاب گیر","کاور","سانروف")) {
 val window=noun.startsWith("شیشه") || noun.startsWith("پنجره")
 val roof=noun=="سانروف"
 for((phrase,delta) in listOf("یکم باز کن" to 10,"کمی ببند" to -10,"یه کم باز کن" to 10)) {
 val result=parser.recognize("$noun رو $phrase")
 assertTrue("$noun $phrase -> $result",result is Recognition.Matched)
 val c=(result as Recognition.Matched).command
 assertEquals(if(window) "ADJUST_WINDOW_POSITION" else if(roof) "ADJUST_SUNROOF_POSITION" else "ADJUST_SUNSHADE_POSITION",c.action)
 assertEquals(delta,c.parameters["deltaPercent"])
 if(window) assertEquals(if(noun.endsWith("ها")) "all" else "driver",c.parameters["window"])
 }
 for(modifier in listOf("نصف","کامل","تا ته","تا انتها","کاملا")) for(op in listOf("باز کن","ببند")) {
 val result=parser.recognize("$noun رو $modifier $op")
 assertTrue("$noun $modifier $op -> $result",result is Recognition.Matched)
 val c=(result as Recognition.Matched).command
 if(modifier=="نصف") assertEquals(50,c.parameters[if(!window && !roof) "position" else "percent"])
 }
 }
 }

 @Test fun reportedPhaseThreeUtterances() {
 val parser=RahaCommandRecognizer()
 val cases=listOf(
 Case("پنجره رو تا نصف باز کن","SET_WINDOW_POSITION",mapOf("window" to "driver","percent" to 50)),
 Case("شیشه رو تا نصف باز کن","SET_WINDOW_POSITION",mapOf("window" to "driver","percent" to 50)),
 Case("یکم شیشه رو بیشتر ببند","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to -10)),
 Case("شیشه رو بکش پایین","OPEN_WINDOW",mapOf("window" to "driver")),
 Case("همه شیشه ها رو ببر","CLOSE_WINDOW",mapOf("window" to "all")),
 Case("قفل کودک","SET_CHILD_LOCK",mapOf("side" to "both","enabled" to true)),
 Case("در باک شارژ رو باز کن","OPEN_CHARGE_PORT",emptyMap()),
 Case("در باک برق رو باز کن","OPEN_CHARGE_PORT",emptyMap()),
 Case("مخزن بنزین رو باز کن","OPEN_FUEL_FLAP",emptyMap()),
 Case("مخزن شارژ رو باز کن","OPEN_CHARGE_PORT",emptyMap()),
 Case("مخزن برق رو باز کن","OPEN_CHARGE_PORT",emptyMap()),
 Case("سانروف رو تا نیمه باز کن","SET_SUNROOF_POSITION",mapOf("percent" to 50)),
 Case("سانروف رو بیشتر باز کن","ADJUST_SUNROOF_POSITION",mapOf("deltaPercent" to 10)),
 Case("شیشه رو بیشتر باز کن","ADJUST_WINDOW_POSITION",mapOf("window" to "driver","deltaPercent" to 10)),
 Case("شیشه رو ببر پایین","OPEN_WINDOW",mapOf("window" to "driver")),
 Case("شیشه رو ببر بالا","CLOSE_WINDOW",mapOf("window" to "driver")),
 Case("پرده رو بیشتر ببند","ADJUST_SUNSHADE_POSITION",mapOf("deltaPercent" to -10))
 )
 for(c in cases) {
 val result=parser.recognize(c.phrase)
 assertTrue("${c.phrase}: $result",result is Recognition.Matched)
 val cmd=(result as Recognition.Matched).command
 assertEquals(c.phrase,c.action,cmd.action)
 assertEquals(c.phrase,c.params,cmd.parameters)
 }
 }

 @Test fun centralDoorLockCommands() {
 val parser=RahaCommandRecognizer()
 for(noun in listOf("درها","در ها","درب ها","درهای ماشین","در ماشین")) {
  for((verb,locked) in listOf("باز کن" to false,"قفل کن" to true,"قفل رو باز کن" to false)) {
   val phrase="$noun رو $verb"
   val result=parser.recognize(phrase)
   assertTrue("$phrase: $result",result is Recognition.Matched)
   val c=(result as Recognition.Matched).command
   assertEquals("SET_DOOR_LOCK",c.action)
   assertEquals(mapOf("door" to "all","locked" to locked),c.parameters)
  }
 }
 val child=parser.recognize("قفل کودک رو فعال کن") as Recognition.Matched
 assertEquals("SET_CHILD_LOCK",child.command.action)
 val trunk=parser.recognize("در صندوق رو باز کن") as Recognition.Matched
 assertEquals("OPEN_TAILGATE",trunk.command.action)
 }

 @Test fun smallAmountAndMisheardClose() {
 val parser=RahaCommandRecognizer()
 for(noun in listOf("پرده","شیشه","پنجره","سانروف"))
  for(amount in listOf("کم ای","یه مقدار","یک مقدار","مقداری")) {
   val result=parser.recognize("$noun رو $amount باز کن")
   assertTrue("$noun $amount: $result",result is Recognition.Matched)
   assertEquals(10,(result as Recognition.Matched).command.parameters["deltaPercent"])
  }
 val closed=(parser.recognize("شیشه رو ببر") as Recognition.Matched).command
 assertEquals("CLOSE_WINDOW",closed.action)
 assertEquals(mapOf("window" to "driver"),closed.parameters)
 val gate=com.abrar.raha.RahaWakeGate(setOf("رها"))
 assertEquals("شیشه رو ببر",gate.final("رها شیشه رو ببر",0))
 val grammar=com.abrar.raha.RahaSpeechVocabulary.wakePhrases()
 for(wake in listOf("رها","هی رها","سلام رها")) assertTrue(grammar.contains("$wake شیشه رو ببند"))
 }
 @Test fun anotherSmallMovementPreservesDirectionAndTarget() {
 val parser=RahaCommandRecognizer()
 for (amount in listOf("یکم", "یه مقدار", "یه ذره", "کمی"))
 for (another in listOf("دیگه", "دیگر"))
 for ((verb,delta) in listOf("بده پایین" to 10,"بکش پایین" to 10,"بده بالا" to -10)) {
 val text="شیشه رو $amount $another $verb"
 val result=parser.recognize(text)
 assertTrue("$text: $result",result is Recognition.Matched)
 val command=(result as Recognition.Matched).command
 assertEquals(text,"ADJUST_WINDOW_POSITION",command.action)
 assertEquals(text,mapOf("window" to "driver","deltaPercent" to delta),command.parameters)
 }
 }
}