package com.abrar.raha

import com.abrar.raha.intent.RahaWordPiece

/** Stable place IDs, independent of coordinates, Android and map applications. */
object RahaPlaces {
    val builtInAliases: Map<String,List<String>> = linkedMapOf(
        "soroush_central" to listOf("نمایندگی مرکزی سروش موتور دیزل", "نمایندگی مرکزی سروش موتور مبنا", "نمایندگی مرکزی سروش دیزل مبنا", "نمایندگی مرکزی سروش موتور", "نمایندگی مرکزی سروش دیزل", "نمایندگی مرکزی سروش", "نمایندگی مرکزی", "تعمیرگاه مرکزی سروش موتور دیزل", "تعمیرگاه مرکزی سروش موتور", "تعمیرگاه مرکزی سروش دیزل", "تعمیرگاه مرکزی سروش", "تعمیرگاه مرکزی", "مرکز خدمات سروش موتور"),
        "shahpary" to listOf("نمایندگی شاهپری پایا", "نمایندگی شاه پری پایا", "نمایندگی شاهپری", "نمایندگی شاه پری", "تعمیرگاه شاهپری", "تعمیرگاه شاه پری", "شاهپری", "شاه پری")
    )
    @Volatile private var userAliases: Map<String,List<String>> = emptyMap()
    @Volatile var revision: Long = 0; private set
    val aliases: Map<String,List<String>> get() = builtInAliases + userAliases
    @Synchronized fun updatePlaces(places: List<RahaSavedPlace>) {
        RahaSavedPlaces.validate(places)
        val next=places.associate { it.id to (listOf(it.name)+it.aliases).map(RahaWordPiece::normalize).distinct() }
        if(next != userAliases) { userAliases=next; revision++ }
    }
    val requests = listOf("بریم", "برو", "ببر", "منو ببر", "من رو ببر", "من را ببر", "ما رو ببر", "مسیریابی کن", "مسیر یابی کن", "مسیریابی", "مسیر یابی", "مسیر بده", "راه رو نشون بده", "راه را نشان بده", "راهنمایی کن", "مقصد رو بذار", "بذار مقصد", "می خوام برم", "میخوام برم", "می خواهم بروم", "قصد دارم برم")
    /** Entity lookup only; intent is always determined by the trained model. */
    fun mentions(text:String): List<Pair<String,String>> {
        val mentioned=aliases.flatMap { (id,names) -> names.map(RahaWordPiece::normalize)
            .filter { (" $text ").contains(" $it ") }.map { id to it } }
        return mentioned.filter { (_,name) -> mentioned.none { (_,other) -> other.length>name.length && (" $other ").contains(" $name ") } }
    }
}
