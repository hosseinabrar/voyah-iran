package com.abrar.raha

/** Domain language material for speech decoding, not a list of exact-match commands.
 * Single words allow new arrangements; phrase examples supply domain word sequences.
 * Add new aliases here alongside recognizer rules and recognition tests.
 */
object RahaSpeechVocabulary {
    val targets = listOf(
        "سرد کن", "سردکن", "خنک کن", "گرم کن", "گرمکن", "ماساژ", "ماساژور",
        "هیتر", "دیفراست جلو", "دیفراست عقب", "تهویه", "سیستم تهویه", "کولر", "بخاری", "ای سی", "کمپرسور", "مکس کولر",
        "مکس ای سی", "حداکثر سرمایش", "سرمایش حداکثری", "دمای اتاق", "دمای کابین", "دمای داخل", "دما",
        "درجه کولر", "درجه بخاری", "فن", "فن کولر", "فن تهویه", "دور فن", "سرعت فن", "شدت باد", "باد کولر", "جهت باد", "جهت وزش باد", "وزش باد", "دریچه باد", "دریچه تهویه", "دریچه کولر",
        "تهویه هوای داخل", "هوای تازه", "گردش هوا", "گردش داخلی", "ورود هوای بیرون", "بخار گیر جلو", "بخار گیر عقب",
        "بخار زدایی جلو", "بخار زدای شیشه جلو", "گرم کن شیشه عقب", "سینک", "همگام سازی"
    )
    val operators = listOf(
        "روشن", "خاموش", "فعال", "اکتیو", "اکتیو کن", "غیر فعال", "روشن کن", "خاموش کن", "راه بنداز",
        "راه بینداز", "زیاد کن", "کم کن", "بیشتر", "کمتر", "بالا", "پایین",
        "گرم تر", "خنک تر", "سرد تر", "اتو", "اوتو", "اتوماتیک", "اتومات", "خودکار", "دستی", "هماهنگ",
        "همگام شه", "همگام بشه", "همگام سازی", "مثل هم", "یکسان شه", "عوض کن", "یکسان کرد", "تا ته زیاد کن", "موجی", "غلتکی", "چرخشی", "دایره ای", "لول سه", "سطح سه", "سطح دو", "سطح یک",
        "در همه جهت", "در همه جهات", "مستقیم", "به سمت صورت", "به صورت", "به پا", "به شیشه", "داخلی", "خارجی", "تازه", "حداکثر", "حداقل"
    )
    val positions = listOf("", "راننده", "شاگرد", "چپ", "راست", "صندلی", "صندلی راننده", "صندلی شاگرد", "دو طرف")
    private val extra = listOf(
        "تا انتها فعال سازی بصورت به شکل خودکار انجام شود بوزه بوزد وضعیت پخش ویوینگ تهویه اتاق ای سی کولر ای سی ماشین مود مد",
        "رها لطفا خواهشا میشه کن کنید کنین کنی بکن رو را و ر برای به روی تو از حالت مد قرار انتخاب تنظیم سمت طرف بده بذار بگذار بزار بشه باشه بیار ببر کردن هم فقط ها های",
        "جهات جهت وزش مستقیم در گرفته تعویض مثل", "لول اسپید", "نکن نکنید نکنین نذار نشه نباشه", "سطح درجه شدت پله اتاق کابین داخل صندلیم سرنشین جلو",
        "صفر یک یه دو سه چهار پنج شش هفت هشت نه ده یازده دوازده سیزده چهارده پانزده شانزده هفده هجده نوزده بیست سی نیم",
        "صورت پا پاها شیشه بیرون خارج داخل چرخشی موج ویو رولینگ سردکن گرمکن بخار گیر بخار زدا اتوماتیک",
        "اینجا خیلی گرمه گرممه سرده سردمه گرمم سردم شده هوا اتاق کابین داخل ماشین می خوام میخوام می خواهم کرد یکسان ته زدایی",
        "همه هر دو طرف آروم آرومتر آرامتر تندتر کاهش افزایش بیشترین کمترین آخر"
    )
    fun phrases(): List<String> = buildSet {
        add("[unk]")
        for(target in listOf("دمای کابین", "دمای اتاق", "دمای داخل ماشین", "دمای داخل خودرو", "دمای داخل", "دمای ماشین", "دمای خودرو"))
            for(verb in listOf("سینک کن", "همگام کن", "یکسان کن", "هماهنگ کن")) add("$target رو $verb")
        for(verb in listOf("بگیر", "بگیرین", "بگیرید", "بگیرم", "بگیریم", "بگیری", "بگیره", "گرفتن", "بزن", "بزنین", "بزنید", "بزنیم", "بزنی", "بزنه")) {
            add(verb); add("کولر $verb"); add("کولر رو $verb")
        }
        for (phrase in RahaColloquialVocabulary.amounts) { add(phrase); addAll(phrase.split(' ')) }
        addAll(listOf("هوا گرم", "هوا سرد", "هوا خیلی گرم", "هوا خیلی سرد", "گرم", "سرد", "بکش", "بکشید", "بدین", "بدید", "بذارین", "بزارین"))
        addAll(listOf("هی رها", "سلام رها", "رها", "هی", "سلام"))
        for(group in RahaPhaseFiveRecognizer.vocabulary) for(phrase in group.split("|")) {add(phrase);addAll(phrase.split(" "))}
        addAll(listOf("دی اکتیو", "اتو هلد رو روشن کن", "اتو هولد رو فعال کن", "نگه دارنده خودکار رو فعال کن"))
        for(group in RahaPhaseFourRecognizer.vocabulary) for(phrase in group.split("|")) {
            if(!phrase.any { it in 'a'..'z' }) { add(phrase); addAll(phrase.split(" ")) }
        }
        for(group in RahaPhaseThreeRecognizer.vocabulary) for(phrase in group.split("|")) {
            if (!phrase.any(Char::isDigit)) { add(phrase); addAll(phrase.split(" ")) }
        }
        for(noun in listOf("عطر", "رایحه", "بو", "بوی گل", "رایحه بامبو", "رایحه مه"))
            for(verb in listOf("پخش کن", "رو پخش کن", "پخش بکن", "را پخش کنید", "رو توی ماشین پخش کن")) add("$noun $verb")
        addAll(RahaPhaseTwoRecognizer.odorExamples)
        addAll(RahaPhaseTwoRecognizer.odorTargets)
        add("کنن")
        addAll(listOf("خیلی گرمه", "هوا خیلی گرمه", "خیلی سرده", "هوای تازه می خوام", "کولر رو تا ته زیاد کن", "شیشه بخار گرفته", "شیشه جلو بخار گرفته", "شیشه عقب بخار گرفته", "هوا رو عوض کن"))
        val bounds = RahaLevelVocabulary.maximum + RahaLevelVocabulary.minimum
        val phaseTwo = RahaPhaseTwoRecognizer.fragranceNames + RahaPhaseTwoRecognizer.saveWords + RahaPhaseTwoRecognizer.recallWords +
            RahaPhaseTwoRecognizer.memoryWords.filterNot { it == "driver" } + listOf("درایور", "رانندگی", "موقعیت", "فعلی", "پروفایل", "شماره", "کانال", "مدت", "زمان", "ساعت", "دقیقه", "شصت", "نامحدود", "دائمی", "همیشه", "با", "غلظت", "ضعیف", "متوسط", "قوی", "مدیوم", "ملایم")
        for (scent in RahaFragranceVocabulary.scents) for (name in scent.spoken) {
            add(name)
            add("رایحه رو روی $name بذار")
            add("عطر $name")
            for (duration in listOf("نیم ساعت", "یک ساعت", "سی دقیقه", "شصت دقیقه", "نامحدود")) {
                add("رایحه $name رو برای $duration فعال کن")
                add("عطر پاش $name با شدت متوسط برای $duration روشن کن")
            }
        }
        for (target in listOf("شیشه", "شیشه راننده", "شیشه شاگرد", "شیشه ها", "پنجره"))
            for (amount in listOf("یکم", "یه مقدار", "یه ذره", "کمی"))
                for (verb in listOf("بده پایین", "بکش پایین", "بده بالا"))
                    add("$target رو $amount دیگه $verb")
        RahaFormalVocabulary.words.keys.forEach { add(it) }
        for(side in listOf("راننده", "شاگرد", "چپ", "راست", "عقب", "جلو", "عقب چپ", "عقب راست"))
            for(verb in listOf("قفل کن", "باز کن", "قفل کنید", "باز کنید")) add("در $side رو $verb")
        for(noun in listOf("در رو", "درب رو", "درارو", "درا رو", "درها رو", "درهارو", "درب ها رو")) {
            add("$noun قفل کن")
            add("$noun باز کن")
        }
        for(noun in listOf("شیشه ها", "همه شیشه ها", "پنجره ها"))
            for(request in listOf("رو ببر تو حالت تهویه", "رو بذار روی حالت تهویه", "رو تو وضعیت تهویه قرار بده", "رو ونت کن", "حالت تهویه رو فعال کن"))
                add("$noun $request")
        for(noun in listOf("دریچه باد", "جهت باد", "دریچه تهویه"))
            for(direction in listOf("رو به رو", "روبرو", "جلو", "صورت", "به سمت پا")) {
                add("$noun $direction")
                add("$noun $direction رو فعال کن")
            }
        for (target in listOf("شیشه", "شیشه راننده", "شیشه شاگرد", "شیشه ها", "پرده", "سانروف")) {
            for (request in listOf("تا نیمه باز کنید", "مقداری باز نمایید", "کاملا ببندید")) add("$target را $request")
        }
        for (target in listOf("شیشه", "شیشه شاگرد", "شیشه راننده")) {
            add("$target را مقداری بالا ببرید")
            add("$target را اندکی پایین بیاورید")
        }
        for (target in listOf("سرعت فن", "دمای اتاق", "شدت ماساژ", "نورپردازی داخلی")) {
            add("$target را افزایش دهید")
            add("$target را کاهش دهید")
        }
        val material = targets + operators + positions + extra + bounds + RahaLevelVocabulary.units + phaseTwo
        for (noun in RahaPhaseTwoRecognizer.fragranceNames) {
            for (verb in listOf("روشن", "خاموش", "اکتیو", "زیاد کن", "کم کن", "متوسط", "حداکثر", "حداقل", "شماره یک", "شماره دو", "شماره سه")) add("$noun $verb")
        }
        for (verb in RahaPhaseTwoRecognizer.saveWords + RahaPhaseTwoRecognizer.recallWords) {
            add("مموری راننده رو $verb کن")
            add("موقعیت صندلی رو $verb کن")
        }
        for (target in listOf("دما", "دمای اتاق", "دمای کولر", "فن کولر", "سرعت فن", "گرم کن صندلی", "سرد کن صندلی", "ماساژ")) {
            for (bound in bounds) {
                add("$target رو روی $bound بذار")
                add("$target $bound")
            }
        }
        material.flatMap { it.split(' ') }.filter(String::isNotBlank).forEach { add(it) }
        for (target in targets) for (operator in operators) {
            add("$target $operator")
            add("$target رو $operator")
        }
        for (target in listOf("سرد کن", "گرم کن", "ماساژ"))
            for (position in positions) for (operator in operators)
                add("$target $position $operator".replace("  ", " "))
        for (n in listOf("یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")) {
            add("بیست و $n")
            add("بیست و $n و نیم")
        }
    }.map { it.replace("عطرپاش", "عطر پاش").replace("نازبو", "ناز بو").replace("سانشید", "سان شید").replace("کامفرت", "کام فرت").replace("اوتینگ", "اوت ینگ").replace("آوتینگ", "آوت ینگ").replace("اتوهلد", "اتو هلد").replace("اتوهولد", "اتو هولد").replace("برفپاککن", "برف پاک کن").replace("نوربالای", "نور بالای").replace("مهشکن", "مه شکن").replace("دیلایت", "دی لایت").replace("امبینت", "ام بینت").replace("آمبینت", "آم بینت").replace("پرفورمانس", "پرفورمنس").replace("ایندیویجوال", "ایندی وی جوال").replace("ایندیویژوال", "ایندی ویژوال").replace("اندیویجوال", "اندی وی جوال") }.distinct().filterNot { phrase -> phrase.split(' ').any { it in excludedWords } }

    /** Joined utterances supply wake-to-command transitions to Vosk's domain graph. */
    fun wakePhrases(): List<String> = buildSet {
        val base=phrases()
        addAll(base)
        val wakes=listOf("هی رها","سلام رها","رها")
        val commands=base.filter { phrase ->
            phrase!="[unk]" && phrase !in wakes &&
                !phrase.startsWith("رها ") && !phrase.startsWith("هی رها ") && !phrase.startsWith("سلام رها ") &&
                phrase.split(' ').size <= 12
        }
        for(wake in wakes) for(command in commands) add("$wake $command")
        for(wake in wakes) for(noun in listOf("شیشه", "شیشه ها", "پنجره", "پرده", "سانروف"))
            for(verb in listOf("ببند", "ببر", "باز کن", "یه مقدار باز کن", "کم ای باز کن"))
                add("$wake $noun رو $verb")
    }.toList()

    // Keep common acoustic competitors out of the domain graph, even if future
    // sample expansion accidentally introduces them. This is not text replacement.
    private val excludedWords = setOf("صبر")
}
