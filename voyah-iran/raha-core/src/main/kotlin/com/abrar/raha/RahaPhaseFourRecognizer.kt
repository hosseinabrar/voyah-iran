package com.abrar.raha
import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Phase four: vehicle-independent desired state, never a direct hardware operation. */
internal object RahaPhaseFourRecognizer {
    val sportIdioms = listOf(
        "آدرنالین", "آدرنالینش رو ببر بالا", "یکم آدرنالین تزریق کن", "هیجان بده", "هیجان رو ببر بالا",
        "می خوام پرواز کنم", "بریم رو مود پرواز", "بریم بترکونیم", "آدرنالین رو زیاد کن",
        "کمی آدرنالین بده", "یه کم هیجان بده", "هیجان بیشتری بده", "آدرنالینش رو افزایش بده",
        "بریم پرواز کنیم", "میخوام پرواز کنم", "بزن بریم بترکونیم", "حالت پرواز رو فعال کن"
    )
    val modes = linkedMapOf(
        "eco" to "اکو|اقتصادی|کم مصرف|اکونومی",
        "comfort" to "کامفورت|کامفرت|کام فرت|راحت|راحتی",
        "sport" to "اسپورت|اسپرت|ورزشی|پرفورمنس|پرفورمانس",
        "snow" to "برف|برفی|اسنو",
        "outing" to "اوتینگ|آوتینگ|اوت ینگ|آوت ینگ|گردش|آفرود|آف رود",
        "personal" to "شخصی|شخصی سازی|پرسونال|کاستوم|ایندیویجوال|ایندیویژوال|اندیویجوال|ایندی وی جوال|ایندی ویژوال|اندی وی جوال|ایندیو جوال"
    )
    val drive = "حالت رانندگی|مود رانندگی|درایو مود|حالت|مود|رانندگی"
    val regen = "بازیابی انرژی|باز یابی انرژی|بازیافت انرژی|ریکاوری انرژی|ریکاوری|ریجن|احیای انرژی"
    val wiper = "حساسیت برف پاک کن|حساسیت برفپاککن|برف پاک کن|برفپاککن"
    private val highwayNames = buildList {
        for (noun in listOf("تعلیق", "ارتفاع"))
            for (adjective in listOf("", "تطبیقی", "اتوماتیک", "اتومات", "خودکار", "هوشمند", "تطبیق پذیر", "آداپتیو", "اداپتیو"))
                for (road in listOf("بزرگراه", "اتوبان"))
                    add(listOf(noun, adjective, road).filter { it.isNotEmpty() }.joinToString(" "))
        add("تعلیق تطبیقی")
    }.joinToString("|")
    val toggles = linkedMapOf(
        RahaAction.SET_HIGHWAY_SUSPENSION_ADAPTATION to highwayNames,
        RahaAction.SET_AUTO_HOLD to "اتوهلد|اتو هلد|اتو هولد|اتوهولد|نگهدارنده خودکار|نگه دارنده خودکار|نگهدارنده اتوماتیک|auto hold",
        RahaAction.SET_HILL_DESCENT_CONTROL to "کنترل حرکت در سرازیری|کنترل سرازیری|کنترل سرپایینی|کنترل سر پایینی|کنترل سرعت در سرازیری|کنترل شیب|کنترل حرکت در شیب|اچ دی سی|hdc",
        RahaAction.SET_STABILITY_CONTROL to "کنترل پایداری|سیستم پایداری|پایداری الکترونیکی|ای اس پی|ای اس سی|esp|esc"
    )
    val height = "ارتفاع تعلیق|ارتفاع ماشین|ارتفاع خودرو|ارتفاع بدنه|ارتفاع|تعلیق"
    val parking = "ترمز پارک|ترمز دستی|ترمزدستی|ای پی بی|epb"
    val vocabulary = sportIdioms + listOf("آدرنالین|ادرنالین|آدرنالینش|ادرنالینش|هیجان|هیجانش|تزریق|پرواز|بترکونیم|بترکانیم|بریم|بزن|بیشتری") + modes.values.toList()+listOf(drive,regen,wiper,parking,height,"نرمال|استاندارد|وسط|میانه|بالاتر|پایینتر|بالاترین|پایین ترین|فشار عمیق پدال|دیپ")+toggles.values+
        listOf("ضعیف|متوسط|قوی|ملایم|کم|زیاد|کمتر|بیشتر|افزایش|کاهش|مدیوم|درگیر|آزاد|رها|بگیر|بکش|بخوابون|حساسیت")
    fun recognize(text:String):Recognition.Matched? {
        val found=mutableListOf<VehicleCommand>()
        // Plain level names remain absolute; "کم/زیاد کن" requests a relative step.
        val relativeRegen = RahaCommandRecognizer.Scan(text).take("کم کن|زیاد کن|کم بکن|زیاد بکن|کم بده|زیاد بده") &&
            !RahaCommandRecognizer.Scan(text).take("روی|بذار|بگذار|تنظیم|قرار")
        fun rule(action:RahaAction, body:RahaCommandRecognizer.Scan.()->Map<String,Any>?) {
            val scan=RahaCommandRecognizer.Scan(text)
            val values=scan.body() ?: return
            if(scan.finished()) found+=VehicleCommand(action.name,action.name,values,describe(action,values))
        }
        rule(RahaAction.SET_DRIVE_MODE) {
            val excitement = take("آدرنالین|ادرنالین|آدرنالینش|ادرنالینش|هیجان|هیجانش")
            val flight = take("پرواز کنم|پرواز کنیم|حالت پرواز|مود پرواز")
            val party = take("بریم بترکونیم|بزن بریم بترکونیم|بریم بترکانیم")
            if (!excitement && !flight && !party) return@rule null
            take("بیشتری|بیشتر|بالا|زیاد|افزایش")
            take("تزریق")
            take("یکم|یه کم|یک کم|1 کم|کمی|مقداری|کم")
            take("بزن")
            take("بریم|برو")
            take("ماشین|خودرو")
            if (power() == false) return@rule null
            mapOf("mode" to "sport")
        }
        for((mode,names) in modes) rule(RahaAction.SET_DRIVE_MODE) {
            if(!take(names)) return@rule null
            take(drive)
            // Vosk often merges spoken "تو مد" into "اتومات" before a driving mode.
            take("اتومات|اتوماتیک")
            take("برو")
            take("ماشین|خودرو")
            val enabled=power()
            if(enabled==false) return@rule null
            mapOf("mode" to mode)
        }
        rule(RahaAction.SET_AUTO_HOLD) {
            if (!take(toggles.getValue(RahaAction.SET_AUTO_HOLD))) return@rule null
            val automatic=take("اتوماتیک|خودکار|اتومات|اتو")
            val deep=take("با فشار عمیق پدال|فشار عمیق پدال|با فشار عمیق|فشار عمیق|فشار زیاد پدال|پدال عمیق|دیپ")
            if (automatic == deep) return@rule null
            if (power() == false) return@rule null
            mapOf("mode" to if(automatic) "automatic" else "deepPedal")
        }
        for((action,names) in toggles.filterKeys { it != RahaAction.SET_AUTO_HOLD }) rule(action) {
            if(!take(names)) return@rule null
            take("سیستم|سامانه")
            val enabled=power() ?: true
            mapOf("enabled" to enabled)
        }
        rule(RahaAction.SET_SUSPENSION_HEIGHT) {
            if(!take(height)) return@rule null
            take("ماشین|خودرو|بدنه")
            levelUnits()
            val bound=levelBound()
            val medium=take("متوسط|وسط|میانه|نرمال|استاندارد|عادی")
            val up=take("زیاد|بیشتر|بالا|بالاتر|افزایش")
            val down=take("کم|کمتر|پایین|پایینتر|کاهش")
            if(up && down || medium && (bound!=null || up || down)) return@rule null
            val value=when {
                bound=="max"->"high"
                bound=="min"->"low"
                medium->"medium"
                up->"high"
                down->"low"
                else->return@rule null
            }
            mapOf("level" to value)
        }
        rule(RahaAction.SET_PARKING_BRAKE) {
            if(!take(parking)) return@rule null
            val release=take("آزاد|رها|بخوابون")
            val engage=take("درگیر|بگیر|بکش")
            val enabled=power()
            if(release && engage || release && enabled==true || engage && enabled==false) return@rule null
            val value=when {release->false;engage->true;enabled!=null->enabled;else->return@rule null}
            mapOf("engaged" to value)
        }
        rule(RahaAction.SET_REGENERATION_LEVEL) {
            if(!take(regen)) return@rule null
            levelUnits()
            val bound=levelBound()
            if(bound!=null) take("زیاد|کم|بیشتر|کمتر")
            if (relativeRegen && bound == null) return@rule null
            val named=when {
                take("متوسط|میانه|مدیوم")->"medium"
                take("ضعیف|ملایم|کم|پایین")->"low"
                take("قوی|زیاد|بالا")->"high"
                else->null
            }
            val n=number()
            val level=when {
                bound=="min"->"low"
                bound=="max"->"high"
                named!=null->named
                n==1.0->"low"
                n==2.0->"medium"
                n==3.0->"high"
                else->return@rule null
            }
            if(n!=null && (bound!=null || named!=null)) return@rule null
            mapOf("level" to level)
        }
        rule(RahaAction.SET_WIPER_SENSITIVITY) {
            if(!take(wiper)) return@rule null
            levelUnits()
            val bound=levelBound()
            if(bound!=null) take("زیاد|کم|بیشتر|کمتر")
            val medium=take("متوسط|مدیوم|میانه|وسط")
            val n=number()
            if(medium && (bound!=null || n!=null)) return@rule null
            if(bound!=null && n!=null) return@rule null
            when {
                medium -> mapOf("level" to "medium")
                bound!=null -> mapOf("level" to bound)
                n!=null && n>=1 && n<=Int.MAX_VALUE && n%1==0.0 -> mapOf("level" to n.toInt())
                else->null
            }
        }
        for((noun,action) in listOf(regen to RahaAction.ADJUST_REGENERATION_LEVEL,wiper to RahaAction.ADJUST_WIPER_SENSITIVITY)) rule(action) {
            if(!take(noun)) return@rule null
            levelUnits()
            if(levelBound()!=null) return@rule null
            if(action==RahaAction.ADJUST_WIPER_SENSITIVITY) {
                val delta=direction() ?: return@rule null
                return@rule mapOf("delta" to delta)
            }
            val up=take(if (relativeRegen) "بیشتر|افزایش|زیاد" else "بیشتر|افزایش")
            val down=take(if (relativeRegen) "کمتر|کاهش|کم" else "کمتر|کاهش")
            if(up==down) return@rule null
            mapOf("delta" to if(up)1 else -1)
        }
        return found.distinctBy {it.action to it.parameters}.singleOrNull()?.let {Recognition.Matched(it)}
    }
    private fun describe(action:RahaAction,p:Map<String,Any>):String {
        val names=mapOf(
            RahaAction.SET_SUSPENSION_HEIGHT to "ارتفاع تعلیق",
            RahaAction.SET_DRIVE_MODE to "حالت رانندگی",RahaAction.SET_REGENERATION_LEVEL to "سطح بازیابی انرژی",
            RahaAction.ADJUST_REGENERATION_LEVEL to "تغییر سطح بازیابی انرژی",
            RahaAction.SET_WIPER_SENSITIVITY to "حساسیت برف‌پاک‌کن",RahaAction.ADJUST_WIPER_SENSITIVITY to "تغییر حساسیت برف‌پاک‌کن",
            RahaAction.SET_HIGHWAY_SUSPENSION_ADAPTATION to "تعلیق تطبیقی بزرگراه",RahaAction.SET_AUTO_HOLD to "اتوهلد",
            RahaAction.SET_HILL_DESCENT_CONTROL to "کنترل سرازیری",RahaAction.SET_STABILITY_CONTROL to "کنترل پایداری",
            RahaAction.SET_PARKING_BRAKE to "ترمز پارک"
        )
        val labels=mapOf("automatic" to "خودکار", "deepPedal" to "فشار عمیق پدال", "eco" to "اکو","comfort" to "کامفورت","sport" to "اسپورت","snow" to "برف","outing" to "اوتینگ",
            "personal" to "شخصی","low" to "ضعیف","medium" to "متوسط","high" to "قوی","min" to "حداقل","max" to "حداکثر")
        if(action==RahaAction.SET_SUSPENSION_HEIGHT) return "ارتفاع تعلیق: "+mapOf("high" to "بالا","medium" to "متوسط","low" to "پایین").getValue(p["level"] as String)
        return names[action]+": "+p.map {(k,v)->when(k) {
            "enabled"->if(v==true)"فعال" else "غیرفعال"
            "engaged"->if(v==true)"درگیر" else "آزاد"
            "delta"->if(v==1)"یک سطح بیشتر" else "یک سطح کمتر"
            else->labels[v] ?: v.toString()
        }}.joinToString("، ")
    }
}
