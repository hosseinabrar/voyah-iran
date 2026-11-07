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
        private val lightRequests by lazy {
            listOf("raha-ambient-requests.tsv", "raha-display-requests.tsv").flatMap { filename ->
                checkNotNull(RahaWordPiece::class.java.getResourceAsStream("/$filename")).bufferedReader().useLines { lines ->
                    lines.map { line ->
                        val (pattern, command) = line.split('\t')
                        Regex(pattern) to command
                    }.toList()
                }
            }
        }
        private val mediaMute = Regex("""(?:صدا|صدای (?:موزیک|موسیقی|آهنگ|رسانه))(?: رو| را|و)? (باز(?:ش)? کن|وصل کن|ببند(?:ش)?|قطع کن)""")
        private val airIntake = Regex("""دریچه(?: هوا)?(?: رو| را)? (باز کن|ببند)""")
        private val airflowRequest = Regex("""(?:جهت باد|باد(?: کولر| بخاری| تهویه)?|دریچه(?: هوا| باد| کولر| تهویه)?)(?: رو| را)? (?:به )?(?:سمت |جهت |روی )?((?:شیشه جلو|شیشه|صورت|پا|بالا|پایین|وسط|جلو)(?: و (?:شیشه جلو|شیشه|صورت|پا|بالا|پایین|وسط|جلو))*)(?: (?:(?:تنظیم|پخش) کن|بوزه|بوزد|بخوره))?""")
        private val outlets = mapOf("صورت" to "صورت","وسط" to "صورت","جلو" to "صورت","پا" to "پا","پایین" to "پا","شیشه جلو" to "شیشه","شیشه" to "شیشه","بالا" to "شیشه")
        private val airflowModes = mapOf("صورت" to "face", "پا" to "feet", "شیشه" to "windshield", "صورت و پا" to "faceAndFeet", "پا و شیشه" to "windshieldAndFeet", "صورت و شیشه" to "faceAndWindshield", "صورت و پا و شیشه" to "all")
        internal fun airflowMode(text: String) = airflowRequest.matchEntire(text)?.let { airflowModes[it.groupValues[1]] }
        internal fun normalizeAirflow(text: String): String {
            lightRequests.firstOrNull { it.first.matches(text) }?.let { return it.second }
            mediaMute.matchEntire(text)?.let { match ->
                val verb = match.groupValues[1]
                return if (verb.startsWith("باز") || verb.startsWith("وصل")) "صدای موزیک رو از میوت دربیار" else "صدای موسیقی رو میوت کن"
            }
            airIntake.matchEntire(text)?.let {
                return if (it.groupValues[1] == "باز کن") "گردش هوای بیرونی رو فعال کن" else "هوای داخل رو بچرخون"
            }
            val match = airflowRequest.matchEntire(text) ?: return text
            val selected = match.groupValues[1].split(" و ").map(outlets::getValue)
            return "جهت باد رو روی "+listOf("صورت","پا","شیشه").filter { it in selected }.joinToString(" و ")+" تنظیم کن"
        }
        private val splitSunroof = Regex("""(?<![\p{L}\p{M}\p{N}_])سان روف(?![\p{L}\p{M}\p{N}_])""")
        private val singleItem = Regex("""(?<![\p{L}\p{M}\p{N}_])(?:یدونه|(?:یه|یک) دونه)(?![\p{L}\p{M}\p{N}_])""")
        private val joinedCount = Regex("""(?<![\p{L}\p{M}\p{N}_])(دو|سه)تا(?![\p{L}\p{M}\p{N}_])""")
        private val fourItems = Regex("""(?<![\p{L}\p{M}\p{N}_])چهارتا(?![\p{L}\p{M}\p{N}_])""")
        private val fragranceNames = listOf(
            Regex("""(?<![\p{L}\p{M}\p{N}_])(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:بامبو|[1۱١]|یک|اول)(?= (?:رو|را)(?![\p{L}\p{M}\p{N}_]))""") to "رایحه بامبو",
            Regex("""(?<![\p{L}\p{M}\p{N}_])(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:گل صد ?توما?نی|گل|اسکارلت پی ?یونی|اسکارلت پیونی|پیونی اسکارلت|اسکارلت|پیونی|پی یونی|[2۲٢]|دو|دوم)(?= (?:رو|را)(?![\p{L}\p{M}\p{N}_]))""") to "رایحه گل صد تومانی",
            Regex("""(?<![\p{L}\p{M}\p{N}_])(?:رایحه|عطر|عطر ?پاش|بوگیر|خوشبو ?کننده) (?:مه نمناک|مه|نمناک|واتری میست|واتر ی میست|واتر میست|واتر ی|واتری|[3۳٣]|سه|سوم)(?= (?:رو|را)(?![\p{L}\p{M}\p{N}_]))""") to "رایحه مه"
        )
        fun normalize(text: String) = text.replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')
            .replace(Regex("[\\p{Z}\\t\\n\\u000B\\f\\r\\u0085]+"), " ").trim()
            .replace(Regex("""(?<![\p{L}\p{M}\p{N}_])فی(?:و)? ول فرست(?![\p{L}\p{M}\p{N}_])"""), "فیول فرست")
            .let(::normalizeAirflow).replace(splitSunroof, "سانروف").replace(joinedCount, "$1 تا").replace(singleItem, "یک").replace(fourItems, "چهار")
            .let { normalized -> fragranceNames.fold(normalized) { value, (pattern, name) -> pattern.replace(value, name) } }
    }
}
