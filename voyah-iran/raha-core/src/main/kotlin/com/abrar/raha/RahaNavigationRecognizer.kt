package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Stable place IDs, independent of coordinates, Android and map applications. */
object RahaNavigationRecognizer {
    val builtInAliases: Map<String,List<String>> = linkedMapOf(
        "soroush_central" to listOf("نمایندگی مرکزی سروش موتور دیزل", "نمایندگی مرکزی سروش موتور مبنا", "نمایندگی مرکزی سروش دیزل مبنا", "نمایندگی مرکزی سروش موتور", "نمایندگی مرکزی سروش دیزل", "نمایندگی مرکزی سروش", "نمایندگی مرکزی", "تعمیرگاه مرکزی سروش موتور دیزل", "تعمیرگاه مرکزی سروش موتور", "تعمیرگاه مرکزی سروش دیزل", "تعمیرگاه مرکزی سروش", "تعمیرگاه مرکزی", "مرکز خدمات سروش موتور"),
        "shahpary" to listOf("نمایندگی شاهپری پایا", "نمایندگی شاه پری پایا", "نمایندگی شاهپری", "نمایندگی شاه پری", "تعمیرگاه شاهپری", "تعمیرگاه شاه پری", "شاهپری", "شاه پری")
    )
    @Volatile private var userAliases: Map<String,List<String>> = emptyMap()
    @Volatile var revision: Long = 0; private set
    val aliases: Map<String,List<String>> get() = builtInAliases + userAliases
    @Synchronized fun updatePlaces(places: List<RahaSavedPlace>) {
        RahaSavedPlaces.validate(places)
        val next=places.associate { it.id to (listOf(it.name)+it.aliases).map(RahaCommandRecognizer::normalize).distinct() }
        if(next != userAliases) { userAliases=next; revision++ }
    }
    val requests = listOf("بریم", "برو", "ببر", "منو ببر", "من رو ببر", "من را ببر", "ما رو ببر", "مسیریابی کن", "مسیر یابی کن", "مسیریابی", "مسیر یابی", "مسیر بده", "راه رو نشون بده", "راه را نشان بده", "راهنمایی کن", "مقصد رو بذار", "بذار مقصد", "می خوام برم", "میخوام برم", "می خواهم بروم", "قصد دارم برم")
    fun recognize(text: String): Recognition? {
        fun contains(phrase: String) = (" $text ").contains(" $phrase ")
        // Prefer a complete longer name over a shorter name contained inside it.
        val snapshot=aliases
        val mentioned=snapshot.flatMap { (id,names)->names.filter(::contains).map { id to it } }
        val selected=mentioned.filter { (_,name)->mentioned.none { (_,other)->other.length>name.length && (" $other ").contains(" $name ") } }.map { it.first }.toSet()
        val matches = snapshot.filterKeys { it in selected }
        if (matches.isEmpty()) return null
        if (matches.size != 1) return Recognition.Unknown("بیش از یک مقصد گفته شد")
        val (id, names) = matches.entries.single()
        var rest = " $text "
        for (phrase in (names + requests).sortedByDescending { it.length }) rest = rest.replace(" $phrase ", " ")
        rest = rest.replace(Regex("\\s+"), " ").trim()
        val fillers = setOf("لطفا", "خواهشا", "رو", "را", "به", "تا", "برای", "سمت", "بسمت", "بریم", "برو", "کن", "کنید", "کنین", "بکن", "بکنید", "بده", "بدهید", "نشون", "بگذار", "بذار", "قرار", "مقصد", "نشان", "مسیریابی", "مسیر", "یابی", "توی", "تو", "با", "برام", "برامون")
        if (rest.isNotEmpty() && rest.split(' ').any { it !in fillers }) return null
        return Recognition.Matched(VehicleCommand("NAVIGATE_TO_SAVED_PLACE", "NAVIGATE_TO_SAVED_PLACE",
            mapOf("destination" to id), "مسیریابی به ${names.first()}"))
    }
}
