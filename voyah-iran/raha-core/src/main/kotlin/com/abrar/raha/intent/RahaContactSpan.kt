package com.abrar.raha.intent

/** Entity extraction after CALL_CONTACT is selected by the trained model. */
object RahaContactSpan {
    private fun valid(name:String)=name.isNotBlank() && name.length<=80 && !Regex("(?U)\\b(?:یه|یک|زنگ|تماس|بزن|بزنی|بگیر|بگیری|رو|را|به|با|بعد|اگر|اگه)\\b").containsMatchIn(name)
    fun extract(raw:String, learned:List<Pair<String,String>>):String? {
        var text=RahaWordPiece.normalize(raw).trim(' ','.','،','؟','!')
        for((before,after) in learned.asReversed()) {
            val name=Regex(Regex.escape(before)+"(.+?)"+Regex.escape(after)).matchEntire(text)?.groupValues?.get(1)?.trim()
            if(name!=null && valid(name))return name
        }
        text=text.replace(Regex("^(?:(?:هی|سلام) )?رها\\s+"),"")
            .replace(Regex("^(?:(?:لطفا|لطف می کنی|میشه|می خوام|میخوام|می خواهم|بی زحمت)\\s+)+"),"")
            .replace(Regex("\\s+(?:لطفا|برام)$"),"")
        for(pattern in patterns) {
            val name=pattern.matchEntire(text)?.groupValues?.get(1)?.trim()
            if(name!=null && valid(name))return name
        }
        return null
    }
    private val patterns=listOf(
        "(?:یه |یک )?زنگ (?:به )?(.+?) (?:بزن|بزنی|بزنید)",
        "(?:یه |یک )?زنگ (?:بزن|بزنی|بزنید) (?:به )?(.+)",
        "(?:به )?(.+?) (?:رو |را )?(?:یه |یک )?زنگ (?:بزن|بزنی|بزنید)",
        "(?:یه |یک )?تماس (?:با |به )(.+?) (?:بگیر|بگیری|بگیرید|برقرار کن|برقرار کنی)",
        "(?:با |به )(.+?) (?:یه |یک )?تماس (?:بگیر|بگیری|بگیرید|برقرار کن|برقرار کنی)",
        "تماس (?:بگیر|بگیری|برقرار کن) (?:با |به )?(.+)",
        "تماس با (.+)","شماره (.+?) (?:رو |را )?(?:بگیر|بگیری)","(.+?) (?:رو|را) بگیر"
    ).map(::Regex)
}
