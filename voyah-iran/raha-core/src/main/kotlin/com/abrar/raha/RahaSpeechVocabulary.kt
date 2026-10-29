package com.abrar.raha

/** Frozen tested Vosk phrases plus live user entities; no intent rules. */
object RahaSpeechVocabulary {
    private val base: List<String> by lazy {
        checkNotNull(javaClass.getResourceAsStream("/raha-speech-phrases.txt")).bufferedReader().use { it.readLines() }
    }
    fun phrases():List<String> = buildSet {
        addAll(base)
        for (noun in listOf("ورود آسان", "خروج آسان", "ورود راحت", "خروج راحت", "ایزی اگزیت", "ایزی انتری", "ایزی اینتری",
            "شارژ زمانبندی شده", "زمان بندی شارژ", "شارژ برنامه ریزی شده", "شارژ سر ساعت", "شارژ در ساعت مشخص", "اسک جول شارژ")) {
            add(noun); addAll(noun.split(' '))
            for (state in listOf("روشن", "خاموش", "فعال", "غیر فعال")) {
                add("$noun رو $state کن")
                add("$noun رو بذار تو حالت $state")
            }
        }
        addAll(RahaContacts.vocabulary())
        for(names in RahaPlaces.aliases.values) for(name in names) {
            add(name);addAll(name.split(' '))
            for(request in RahaPlaces.requests) add("$request $name")
        }
    }.map(RahaInputText::spaces).filter(String::isNotBlank).distinct()

    /** Joined utterances supply wake-to-command transitions to Vosk's domain graph. */
    fun wakePhrases(): List<String> = buildSet {
        val base=phrases()
        addAll(base)
        val wakes=listOf("هی رها","سلام رها","رها")
        val commands=base.filter { phrase ->
            phrase!="[unk]" && phrase !in wakes && !RahaIgnoredSpeech.matches(phrase) &&
                !phrase.startsWith("رها ") && !phrase.startsWith("هی رها ") && !phrase.startsWith("سلام رها ") &&
                phrase.split(' ').size <= 12
        }
        for(wake in wakes) for(command in commands) add("$wake $command")
        for(wake in wakes) for(noun in listOf("شیشه", "شیشه ها", "پنجره", "پرده", "سانروف"))
            for(verb in listOf("ببند", "ببر", "باز کن", "یه مقدار باز کن", "کم ای باز کن"))
                add("$wake $noun رو $verb")
    }.toList()

}
