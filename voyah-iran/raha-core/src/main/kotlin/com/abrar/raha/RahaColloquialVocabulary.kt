package com.abrar.raha

/** Quantity modifiers are canonicalized before «یه/یک» can become numeric levels. */
internal object RahaColloquialVocabulary {
    val amounts = listOf("یکم", "یکمی", "یه کم", "یک کم", "کمی", "کم ای", "کم یه", "کمه",
        "یه مقدار", "یک مقدار", "مقداری", "اندکی", "یه ذره", "یک ذره", "ذره ای", "یه خورده", "یک خورده", "یه خرده", "یک خرده")
    private val pattern = Regex("(?<!\\S)(?:" + amounts.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?!\\S)")
    fun normalize(text: String) = pattern.replace(text, "کمی")
        .replace(Regex("(?<!\\S)کم(?= (?:زیاد|بیشتر|افزایش|بالا|تندتر)(?!\\S))"), "کمی")
}
