package com.abrar.raha

/** Explicit non-command acoustic alternatives, shared by wake and command recognition. */
object RahaIgnoredSpeech {
    val phrases = listOf("رهایم کن", "رها یم کن", "آدما", "ادما", "آدم ها")
    private val pattern = Regex("""(?<![\p{L}\p{N}])(?:رهایم\s+کن|رها\s+یم\s+کن|آدما|ادما|آدم\s*ها)(?![\p{L}\p{N}])""")
    fun matches(text: String): Boolean = pattern.containsMatchIn(
        text.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
    )
}
