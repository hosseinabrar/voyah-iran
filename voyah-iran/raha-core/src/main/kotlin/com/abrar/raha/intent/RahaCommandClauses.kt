package com.abrar.raha.intent

/** Validation-only normalization; model input and user transcript stay intact. */
object RahaCommandClauses {
    private const val left = "(?<![\\p{L}\\p{M}\\p{N}_])"
    private const val right = "(?![\\p{L}\\p{M}\\p{N}_])"
    fun clean(original:String):String {
        var text=Regex("^(?:رها\\s+)?(?:اگر|اگه)\\s+(?:ممکنه|ممکن است|می\\s*تونی|می\\s*توانی)\\s+").replace(original,"")
        val on="(?:روشن|(?<!غیر )فعال)"
        val off="(?:خاموش|غیر\\s*فعال)"
        for((source,target) in listOf(on to off,off to on)) {
            text=Regex(left+source+"\\s+نکن\\s+(?:فقط|در\\s+عوض)\\s+("+target+"(?:ش)?\\s+کن)"+right).replace(text) { it.groupValues[1] }
        }
        for((source,target) in listOf("خاموش" to "روشن","روشن" to "خاموش")) {
            text=Regex(left+"از\\s+(?:حالت\\s+)?"+source+"\\s+به\\s+(?:حالت\\s+)?"+target+"\\s+(?:تغییر(?:ش)?\\s+بده|تبدیل\\s+کن|ببر)"+right).replace(text,target+" کن")
        }
        for((state,destination) in listOf(on to "خاموش",off to "روشن")) {
            text=Regex(left+"از\\s+حالت\\s+"+state+"\\s+(?:در\\s*بیار|خارج\\s+کن)"+right).replace(text,destination+" کن")
        }
        return text
    }
    fun blocked(text:String):Boolean {
        val cleaned=clean(text)
        return Regex(left+"(?:نکن|نبند|نذار|اگر|اگه|آیا|چرا)"+right).containsMatchIn(cleaned) || '?' in cleaned || '؟' in cleaned
    }
}
