package com.abrar.voyahiran.feature.navigation

/** Offline pronunciation: curated names, morphology, explicit vowel marks, then G2P.
 * Unknown-name predictions remain approximate; Persian rendering is being tested. */
internal object ClusterLatinText {
    // Long phrases first; Unicode letter/number boundaries preserve names like سیدان/شهیدی.
    private val removableTitles = Regex(
        "(?<![\\p{L}\\p{N}])(?:پل\\s+طبقاتی|دسترسی\\s+محلی|حجت\\s*الاسلام|آیت\\s*الله|" +
        "زیر\\s*گذر|کنار\\s*گذر|کند\\s*رو|کوچه|خیابان|اتوبان|بزرگراه|شهید|دکتر|سید|پل|بلوار|سردار|استاد|پروفسور|شرقی|غربی|شمالی|جنوبی|مرکزی)(?![\\p{L}\\p{N}])"
    )
    fun shortRoadName(text: String): String {
        val normalized = ClusterNavigationText.clean(text.replace('ك', 'ک').replace('ي', 'ی').replace('ى', 'ی')
            .replace('أ', 'ا').replace('إ', 'ا')
            .replace("\u0640", ""))
        val shortened = tidy(removePreservingMarks(normalized, removableTitles))
        val withoutMartyrs = tidy(removePreservingMarks(shortened, martyrTitle))
        // "میدان شهدا" and "خیابان شهدا" still name Shohada, not an empty road.
        val hasOtherName = withoutMartyrs.split(' ').any {
            it !in setOf("میدان", "تونل", "شمالی", "جنوبی", "شرقی", "غربی", "مرکزی") && it.any(Char::isLetterOrDigit)
        }
        return if (hasOtherName) withoutMartyrs else shortened
    }
    private val martyrTitle = Regex("(?<![\\p{L}\\p{N}])(?:شهدای|شهداء|شهدا)(?![\\p{L}\\p{N}])")
    private fun tidy(value: String) = value.replace(Regex("\\s+"), " ").trim(' ', '-', '–', '،', ',')

    private val words = mapOf(
        "همت" to "hemmat",
        "مدیریت" to "modiriat",
        "چمران" to "chamran",
        "وزرا" to "vozara",
        "وزراء" to "vozara",
        "شهدا" to "shohada",
        "شهداء" to "shohada",
        "شهدای" to "shohada",
        "تونل" to "tunnel",
        "میدان" to "meydan",
        "متر" to "m",
        "کیلومتر" to "km",
        "علامه" to "allameh",
        "طباطبایی" to "tabatabaei",
        "و" to "o",
        "آل احمد" to "al ahmad",
        "سعیدی" to "saeidi",
        "حقانی" to "haghani",
        "مدرس" to "modarres",
        "کشاورز" to "keshavarz",
        "وحید دستجردی" to "vahid dastjerdi",
        "ظفر" to "zafar",
        "خرداد" to "khordad",
        "آذر" to "azar",
        "شهریور" to "shahrivar",
        "آزادی" to "azadi",
        "حسن بنا" to "hasan banna",
        "اسکندری" to "eskandari",
        "اقبال لاهوری" to "eghbal lahouri",
        "امام خمینی" to "emam khomeini",
        "امیرکبیر" to "amirkabir",
        "امیر کبیر" to "amirkabir",
        "انقلاب" to "enghelab",
        "مدنی" to "madani",
        "باهنر" to "bahonar",
        "نیاوران" to "niavaran",
        "حکمت" to "hekmat",
        "بهشتی" to "beheshti",
        "عباس آباد" to "abbas abad",
        "پاتریس لومومبا" to "patris lumumba",
        "پاسداران" to "pasdaran",
        "پیروزی" to "pirouzi",
        "توحید" to "tohid",
        "جانبازان" to "janbazan",
        "جمهوری" to "jomhouri",
        "خالد اسلامبولی" to "khaled eslamboli",
        "خدامی" to "khodami",
        "خرمشهر" to "khorramshahr",
        "رجایی" to "rajaei",
        "زمرد" to "zomorrod",
        "قرنی" to "gharani",
        "ستارخان" to "sattarkhan",
        "ستار خان" to "sattarkhan",
        "سعدی" to "saadi",
        "سمیه" to "somayyeh",
        "سهروردی" to "sohrevardi",
        "شریعتی" to "shariati",
        "دیباجی" to "dibaji",
        "فلاحی" to "fallahi",
        "فیاضی" to "fayyazi",
        "لواسانی" to "lavasani",
        "کلاهدوز" to "kolahdooz",
        "شیرازی" to "shirazi",
        "صابونچی" to "saboonchi",
        "طالقانی" to "taleghani",
        "عباسپور" to "abbaspour",
        "فاطمی" to "fatemi",
        "فلسطین" to "felestin",
        "قائم مقام فراهانی" to "ghaem magham farahani",
        "ولی عصر" to "valiasr",
        "ولیعصر" to "valiasr",
        "مصطفی خمینی" to "mostafa khomeini",
        "مطهری" to "motahhari",
        "مفتح" to "mofatteh",
        "مقدس اردبیلی" to "moghaddas ardabili",
        "میرداماد" to "mirdamad",
        "میر داماد" to "mirdamad",
        "میرزای شیرازی" to "mirzaye shirazi",
        "نامجو" to "namjoo",
        "نجات الهی" to "nejatollahi",
        "نوفل لوشاتو" to "nofel loshato",
        "وحدت اسلامی" to "vahdat eslami",
        "کارگر" to "kargar",
        "تیر" to "tir",
        "سعادت آباد" to "saadat abad",
        "امام حسین" to "emam hossein",
        "رازی" to "razi",
        "قدس" to "ghods",
        "قیام" to "ghiam",
        "رسالت" to "resalat",
        "نیایش" to "niayesh",
        "یادگار امام" to "yadegar emam",
        "یادگار" to "yadegar",
        "امام" to "emam",
        "صیاد شیرازی" to "sayyad shirazi",
        "آبشناسان" to "abshenasan",
        "باکری" to "bakeri",
        "حکیم" to "hakim",
        "مرزداران" to "marzdaran",
        "جنت آباد" to "jannat abad",
        "آباد" to "abad",
        "سی" to "si",
        "بیست" to "bist",
        "چهل" to "chehel",
        "پنجاه" to "panjah",
        "شصت" to "shast",
        "هفتاد" to "haftad",
        "هشتاد" to "hashtad",
        "نود" to "navad",
        "صد" to "sad",
        "یک" to "yek",
        "دو" to "do",
        "سه" to "se",
        "چهار" to "chahar",
        "پنج" to "panj",
        "شش" to "shesh",
        "هفت" to "haft",
        "هشت" to "hasht",
        "نه" to "noh",
        "ده" to "dah",
        "یازده" to "yazdah",
        "دوازده" to "davazdah",
        "سیزده" to "sizdah",
        "چهارده" to "chahardah",
        "پانزده" to "panzdah",
        "شانزده" to "shanzdah",
        "هفده" to "hefdah",
        "هجده" to "hejdah",
        "نوزده" to "noozdah",
        "اول" to "avval",
        "یکم" to "yekom",
        "دوم" to "dovom",
        "سوم" to "sevom",
        "چهارم" to "chaharom",
        "پنجم" to "panjom",
        "ششم" to "sheshom",
        "هفتم" to "haftom",
        "هشتم" to "hashtom",
        "نهم" to "nohom",
        "دهم" to "dahom",
        "یازدهم" to "yazdahom",
        "دوازدهم" to "davazdahom",
        "سیزدهم" to "sizdahom",
        "چهاردهم" to "chahardahom",
        "پانزدهم" to "panzdahom",
        "شانزدهم" to "shanzdahom",
        "هفدهم" to "hefdahom",
        "هجدهم" to "hejdahom",
        "نوزدهم" to "noozdahom",
        "بیستم" to "bistom",
        "سی ام" to "siom",
        "چهل ام" to "chehelom",
        "پنجاهم" to "panjahom",
        "شصتم" to "shastom",
        "هفتادم" to "haftadom",
        "هشتادم" to "hashtadom",
        "نودم" to "navadom",
        "صدم" to "sadom"
    )
    private val letters = mapOf('ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ث' to "s",
        'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z",
        'ژ' to "zh", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z", 'ع' to "a",
        'غ' to "gh", 'ف' to "f", 'ق' to "gh", 'ک' to "k", 'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n",
        'و' to "v", 'ه' to "h", 'ی' to "i", 'ئ' to "y", 'ؤ' to "v", 'ء' to "", 'ة' to "h")
    private val marks = Regex("[\u064B-\u065F\u0670]")
    private fun bare(text: String) = marks.replace(text, "")
    private fun removePreservingMarks(text: String, pattern: Regex): String {
        val indices = text.indices.filter { !marks.matches(text[it].toString()) }
        val plain = indices.map { text[it] }.joinToString("")
        val out = StringBuilder(text)
        for (match in pattern.findAll(plain).toList().asReversed()) {
            val start = indices[match.range.first]
            val end = indices.getOrNull(match.range.last + 1) ?: text.length
            out.replace(start, end, " ")
        }
        return out.toString()
    }
    fun convert(text: String): String = convertName(shortRoadName(text))
    fun convertName(text: String): String {
        val tokens = text.replace(Regex("[،,؛;:()\\[\\]«»]"), " ")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var found = false
            for (size in minOf(4, tokens.size - i) downTo 1) {
                val key = tokens.subList(i, i + size).joinToString(" ")
                val plain = bare(key)
                val pronunciation = words[plain] ?: PersianRoadWords.words[plain] ?:
                    if (marks.containsMatchIn(key)) null else compound(plain)
                if (pronunciation != null) {
                    out += pronunciation; i += size; found = true; break
                }
            }
            if (!found) {
                val token = tokens[i]
                out += (if (marks.containsMatchIn(token)) null else PersianG2p.predict(token))
                    ?: transliterateMarked(token)
                i++
            }
        }
        return ClusterNavigationText.limitUtf8(out.filter { it.isNotBlank() }.joinToString(" "), 180)
    }
    private fun known(word: String): String? = words[word] ?: PersianRoadWords.words[word]
    private val nameSuffixes = linkedMapOf("آبادی" to "abadi", "ابادی" to "abadi",
        "آباد" to "abad", "اباد" to "abad", "شهر" to "shahr", "پور" to "pour",
        "نژاد" to "nezhad", "زاده" to "zadeh", "وند" to "vand", "ستان" to "estan")
    private val namePrefixes = listOf("گل", "محمد", "احمد", "محمود", "حسین", "حسن", "علی", "رضا", "امیر", "عباس", "نبی")
    private fun compound(value: String): String? {
        // Bound work even for malformed/unusually long source strings. Never guess missing vowels.
        if (value.length > 60) return null
        val joined = value.replace(" ", "")
        if (joined != value) known(joined)?.let { return it }
        for ((suffix, sound) in listOf("های" to "haye", "ها" to "ha")) {
            if (joined.endsWith(suffix)) {
                val stem = joined.dropLast(suffix.length)
                if (stem.length >= 2) (known(stem) ?: nameCompound(stem))?.let { return it + sound }
            }
        }
        return nameCompound(joined, allowPrefixes = ' ' !in value)
    }
    private fun nameCompound(word: String, allowPrefixes: Boolean = true): String? {
        for ((suffix, sound) in nameSuffixes) {
            if (word.endsWith(suffix)) {
                val stem = word.dropLast(suffix.length)
                if (stem.length >= 2) known(stem)?.let { return it + sound }
            }
        }
        if (!allowPrefixes) return null
        for (prefix in namePrefixes) {
            if (word.startsWith(prefix)) {
                val rest = word.drop(prefix.length)
                if (rest.length >= 2) known(rest)?.let { ending -> known(prefix)?.let { return it + ending } }
            }
        }
        return null
    }
    private fun transliterateMarked(word: String): String = buildString {
        var i = 0
        while (i < word.length) {
            val c = word[i++]
            val diacritics = StringBuilder()
            while (i < word.length && marks.matches(word[i].toString())) diacritics.append(word[i++])
            val sound = when {
                c in '۰'..'۹' -> ('0' + (c - '۰')).toString()
                c in '٠'..'٩' -> ('0' + (c - '٠')).toString()
                c == '٫' -> "."
                letters.containsKey(c) -> letters.getValue(c)
                c.code in 32..126 -> c.toString()
                else -> ""
            }
            if (!(c == 'ا' && endsWith("a"))) append(sound)
            if ('ّ' in diacritics) append(sound) // shadda doubles the consonant, not the vowel
            append(when {
                'ً' in diacritics -> "an"
                'ٍ' in diacritics -> "en"
                'ٌ' in diacritics -> "on"
                'َ' in diacritics -> "a"
                'ِ' in diacritics -> "e"
                'ُ' in diacritics -> "o"
                'ٰ' in diacritics -> "a"
                else -> ""
            })
        }
    }
}
