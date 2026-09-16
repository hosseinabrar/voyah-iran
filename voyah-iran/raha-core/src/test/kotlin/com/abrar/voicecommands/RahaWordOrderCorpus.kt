package com.abrar.raha

/** Regression corpus of natural word orders; deliberately not injected into the speech graph. */
internal object RahaWordOrderCorpus {
    fun phrases():List<String> = buildSet {
        fun setting(target:String,value:String) {
            add("$target رو بذار رو $value")
            add("$target رو رو $value تنظیم کن")
            add("$target رو تنظیم کن رو $value")
            add("روی $value تنظیم کن $target رو")
            add("تنظیم کن $target رو روی $value")
            add("$target را روی $value تنظیم نمایید")
            add("لطفا روی $value $target را تنظیم کنید")
        }
        val temperatures=listOf("شانزده","هفده","هجده","نوزده","بیست","بیست و یک","بیست و دو",
            "بیست و سه","بیست و چهار","بیست و پنج","بیست و شش","بیست و هفت","بیست و هشت","بیست و نه","سی","بیست و دو و نیم")
        for(target in listOf("دما","دمای کابین","دمای راننده","دمای شاگرد"))
            for(value in temperatures) setting(target,"$value درجه")
        for(target in listOf("فن","سرعت فن","دور فن"))
            for(value in listOf("یک","دو","سه","چهار","پنج","شش","هفت","حداکثر","حداقل"))
                setting(target,value)
        for(target in listOf("گرمکن صندلی راننده","گرمکن صندلی شاگرد","سردکن صندلی راننده","سردکن صندلی شاگرد","ماساژ صندلی راننده"))
            for(value in listOf("سطح یک","سطح دو","سطح سه")) setting(target,value)
        for((target,values) in mapOf(
            "ماساژ صندلی راننده" to listOf("موجی","چرخشی"),
            "شدت رایحه" to listOf("کم","متوسط","زیاد"),
            "ارتفاع ماشین" to listOf("بالا","متوسط","پایین"),
            "ماشین" to listOf("اقتصادی","کامفورت","اسپورت","برفی","شخصی"),
            "بازیابی انرژی" to listOf("ضعیف","متوسط","قوی"),
            "حساسیت برف پاک کن" to listOf("کم","متوسط","زیاد"),
            "چراغ ها" to listOf("اتوماتیک","نور پایین"),
            "نورپردازی داخلی" to listOf("حداکثر","حداقل")
        )) for(value in values)setting(target,value)
        for(target in listOf("شیشه راننده","شیشه شاگرد","شیشه ها","پرده","سانروف"))
            for(amount in listOf("کمی","تا نصف","تا نیمه","کاملا")) {
                add("$target رو $amount باز کن")
                add("$amount باز کن $target رو")
                add("باز کن $target رو $amount")
                add("$target را $amount باز کنید")
            }
        for(target in listOf("تهویه","کولر","بخاری","ای سی","گرمکن صندلی راننده","سردکن صندلی شاگرد","رایحه","نورپردازی داخلی"))
            for(verb in listOf("روشن کن","خاموش کن")) {
                add("$verb $target رو")
                add("$target رو $verb")
                add("لطفا $target را $verb")
            }
    }.toList()
}
