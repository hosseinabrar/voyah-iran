package com.abrar.raha

internal object RahaFragranceVocabulary {
    data class Scent(val id: String, val label: String, val aliases: List<String>, val spoken: List<String>)
    val scents = listOf(
        Scent("freshBamboo", "Fresh Bamboo", listOf("fresh bamboo", "فرش بامبو", "فرش بمبو", "فریش بامبو", "بامبوی تازه", "بامبو"), listOf("فرش بامبو", "فرش بمبو", "فریش بامبو", "بامبوی تازه", "بامبو")),
        Scent("peonyScarlet", "Peony Scarlet", listOf("peony scarlet", "پیونی اسکارلت", "پئونی اسکارلت", "پی اونی اسکارلت", "پونی اسکارلت", "گل صدتومانی سرخ", "گل صد تومانی سرخ", "گل صدتومانی", "گل صد تومانی", "گل", "صد تومنی", "صد تومانی", "صدتومانی", "پیونی", "پی اونی", "پونی", "ناز بو", "نازبو"), listOf("پی اونی اسکارلت", "پونی اسکارلت", "گل صد تومانی سرخ", "گل صد تومانی", "گل", "صد تومنی", "صد تومانی", "پی اونی", "ناز بو")),
        Scent("wateryMist", "Watery Mist", listOf("watery mist", "واتری میست", "واتر ی میست", "واتر میست", "مه آبی", "مه نمناک", "مه نمدار", "نمناک", "نمدار", "مه"), listOf("واتر ی میست", "واتر میست", "مه آبی", "مه نمناک", "مه نمدار", "نمناک", "نمدار", "مه"))
    ).map { scent ->
        val extra=when(scent.id) {
            "freshBamboo" -> listOf("بامبو فرش", "بامبو فریش", "بامبو تازه", "تازه بامبو", "بمبو", "بمبو فرش", "bamboo fresh")
            "peonyScarlet" -> listOf("اسکارلت", "اسکارلت پیونی", "اسکارلت پی اونی", "اسکارلت پونی", "اسکارلت پئونی", "scarlet", "scarlet peony")
            else -> listOf("میست", "میست واتری", "میست واتر ی", "میست واتر", "آبی مه", "نمناک مه", "نمدار مه", "mist", "mist watery")
        }
        scent.copy(aliases=(scent.aliases+extra).distinct(), spoken=(scent.spoken+extra).distinct())
    }
}
