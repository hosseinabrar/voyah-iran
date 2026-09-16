package com.abrar.raha

/** Token-boundary inflections only; target, direction, number and negation are preserved. */
internal object RahaFormalVocabulary {
    val words=mapOf(
        "کنید" to "کن", "بکنید" to "کن", "نمایید" to "کن", "فرمایید" to "کن", "بفرمایید" to "کن",
        "دهید" to "بده", "بدهید" to "بده", "بیاورید" to "بیار", "آورید" to "بیار",
        "ببرید" to "ببر", "بکشید" to "بکش", "بگذارید" to "بگذار", "گذارید" to "بگذار",
        "ببندید" to "ببند", "بگشایید" to "باز", "بگشا" to "باز",
        "بشوید" to "بشه", "گردد" to "شود", "بگردد" to "شود",
        "خواهشمندم" to "لطفا", "لطف" to "لطفا", "دیگر" to "دیگه"
    )
    fun normalize(text:String)=text.split(' ').joinToString(" ") { words[it] ?: it }
}
