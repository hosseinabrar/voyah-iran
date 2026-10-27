package com.abrar.raha.intent

/** The exported ParsBERT tokenizer, not the old command grammar. */
class RahaWordPiece(vocabulary: List<String>) {
    private val vocab = vocabulary.withIndex().associate { it.value to it.index.toLong() }
    data class Encoded(val ids: LongArray, val count: Int)
    fun encode(raw: String): Encoded {
        val text = normalize(raw)
        val words = mutableListOf<String>()
        val word = StringBuilder()
        fun flush() { if (word.isNotEmpty()) { words += word.toString(); word.setLength(0) } }
        text.codePoints().forEach { cp ->
            val category = Character.getType(cp)
            when {
                cp == 0 || cp == 0xfffd -> Unit
                Character.isWhitespace(cp) || category == Character.SPACE_SEPARATOR.toInt() -> flush()
                category == Character.CONTROL.toInt() || category == Character.FORMAT.toInt() -> Unit
                punctuation(cp) || chinese(cp) -> { flush(); words += String(Character.toChars(cp)) }
                else -> word.appendCodePoint(cp)
            }
        }
        flush()
        val tokens = mutableListOf<Long>()
        for (token in words) {
            val points = token.codePoints().toArray()
            if (points.size > 100) { tokens += 1L; continue }
            val pieces = mutableListOf<Long>()
            var start = 0
            var bad = false
            while (start < points.size) {
                var end = points.size
                var found: Long? = null
                while (end > start) {
                    val part = (if (start == 0) "" else "##") + String(points, start, end - start)
                    found = vocab[part]
                    if (found != null) break
                    end--
                }
                if (found == null) { bad = true; break }
                pieces += found
                start = end
            }
            if (bad) tokens += 1L else tokens += pieces
        }
        return Encoded((listOf(2L) + tokens.take(46) + 3L).toLongArray(), tokens.size + 2)
    }
    private fun punctuation(cp: Int): Boolean = cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 ||
        Character.getType(cp) in setOf(20,21,22,23,24,29,30)
    private fun chinese(cp: Int) = cp in 0x4e00..0x9fff || cp in 0x3400..0x4dbf || cp in 0x20000..0x2a6df ||
        cp in 0x2a700..0x2b73f || cp in 0x2b740..0x2b81f || cp in 0x2b820..0x2ceaf || cp in 0xf900..0xfaff || cp in 0x2f800..0x2fa1f
    companion object {
        fun normalize(text: String) = text.replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')
            .replace(Regex("[\\p{Z}\\t\\n\\u000B\\f\\r\\u0085]+"), " ").trim()
    }
}
