package com.abrar.raha

/** Contact names are data, never executable commands. Full-name matches take precedence. */
object RahaContacts {
    /** OEM phonebook exports each number as a row, in its original insertion order. */
    fun <T> firstNumberPerContact(rows:List<T>, name:(T)->String):List<T> = rows.distinctBy { normalize(name(it)) }
    @Volatile var names: List<String> = emptyList(); private set
    @Volatile var revision = 0L; private set
    @Synchronized fun update(values: List<String>) {
        val next = values.map(::normalize).filter(String::isNotBlank).distinct().sorted()
        if (next != names) { names = next; revision++ }
    }
    fun normalize(value: String) = value.replace('ي','ی').replace('ك','ک')
        .map { c -> when(c) { in '۰'..'۹' -> '0'+(c-'۰'); in '٠'..'٩' -> '0'+(c-'٠'); else -> c } }.joinToString("")
        .replace('\u200c',' ').replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()
    private val patterns = listOf(
        Regex("^(?:لطفا |لطفاً )?(?:زنگ بزن|زنگ بزنید|تماس برقرار کن|تماس برقرار کنید|تماس بگیر|تماس بگیرید|تماس) (?:به |با )?(.+)$"),
        Regex("^(?:لطفا |لطفاً )?(?:به |با )?(.+?) (?:رو |را )?(?:زنگ بزن|زنگ بزنید|تماس برقرار کن|تماس برقرار کنید|تماس بگیر|تماس بگیرید|بگیر|بگیرید)$"),
        Regex("^(?:گرفتن شماره|شماره) (.+?)(?: رو بگیر| را بگیرید)$")
    )
    fun target(text: String): String? {
        val clean = normalize(text).replace(Regex("^(?:(?:هی|سلام) )?رها "), "")
            .replace(Regex("(?<!\\S)(?:یه|یک) (?=(?:زنگ بزن(?:ید)?|تماس (?:بگیر(?:ید)?|برقرار کن(?:ید)?))(?!\\S))"), "")
        val extracted=patterns.firstNotNullOfOrNull { it.matchEntire(clean)?.groupValues?.get(1)?.let(::normalize) } ?: return null
        val name=extracted.removePrefix("شماره ").trim()
        return (RahaDefaultContacts.find(name)?.name ?: name).takeIf(String::isNotBlank)
    }
    fun matching(target: String, candidates: List<String>): List<Int> {
        val wanted = normalize(target)
        if (wanted.isBlank()) return emptyList()
        val normalized = candidates.map(::normalize)
        val exact = normalized.indices.filter { normalized[it] == wanted }
        return exact.ifEmpty { normalized.indices.filter { (" ${normalized[it]} ").contains(" $wanted ") } }
    }
    val affirmative = setOf("بله", "آره", "اره", "درسته", "درست است", "بله تماس بگیر", "آره تماس بگیر", "بله بگیر", "آره بگیر", "تایید", "تأیید", "بله درسته", "آری")
    fun confirmed(text: String) = normalize(text) in affirmative
    fun vocabulary(): List<String> = buildSet {
        if (names.isEmpty()) return@buildSet
        for(contact in RahaDefaultContacts.entries) for(alias in contact.aliases.filter { it.none(Char::isDigit) }) {
            add(alias); addAll(alias.split(' ')); add("زنگ بزن به $alias")
        }
        addAll(listOf("شماره", "برقرار", "برقرار کن", "برقرار کنید", "تماس برقرار کن", "زنگ بزن به شماره"))
        addAll(affirmative); addAll(listOf("نه", "خیر", "لغو", "کنسل", "نمی خوام", "اشتباهه", "زنگ بزن", "تماس بگیر", "تماس با", "بگیرید", "بزنید"))
        for (name in names) { add(name); addAll(name.split(' ')); add("با $name تماس بگیر"); add("$name رو بگیر"); add("زنگ بزن به $name") }
    }.toList()
}
