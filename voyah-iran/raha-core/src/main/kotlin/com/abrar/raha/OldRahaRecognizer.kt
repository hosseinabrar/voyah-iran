package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand
import java.io.InputStream
import java.util.Base64

/** Version 2.1.1 grammar plus explicit saved examples. No neural model or fuzzy fallback. */
class OldRahaRecognizer(private val lessonsInput: () -> InputStream = {
    checkNotNull(OldRahaRecognizer::class.java.getResourceAsStream("/raha-old-lessons.tsv"))
}) : AutoCloseable {
    private val grammar = RahaCommandRecognizer()
    private var lessons: Map<String, Recognition>? = null
    var source: String = ""; private set
    @Synchronized fun prepare() {
        if (lessons != null) return
        lessons = lessonsInput().bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).associate { line ->
            val fields = line.split('\t')
            val params = fields.drop(2).associate { field ->
                val parts=field.split(':',limit=3); val value=decode(parts[2])
                parts[0] to when(parts[1]) { "b" -> value.toBooleanStrict(); "n" -> value.toDouble(); else -> value }
            }
            decode(fields[0]) to if(fields[1]=="UNKNOWN") Recognition.Unknown("جمله در آموزش‌ها نامفهوم ثبت شده است")
                else Recognition.Matched(VehicleCommand(fields[1],fields[1],params,fields[1]))
        } }
    }
    @Synchronized fun recognize(input:String):Recognition {
        val raw=RahaMidpointRequests.normalizeObjectMarker(RahaLightWords.normalize(input))
        prepare()
        lessons!![key(raw)]?.let { source="saved-example"; return it }
        RahaMidpointRequests.resolve(raw,::recognizeExact)?.let { source="midpoint-rule"; return it }
        // Repair only a complete, unambiguous decrease; never replace the verb کن globally.
        val repaired=RahaDecreaseSpeechRepair.candidates(raw).map(::recognizeExact)
            .filter(RahaDecreaseSpeechRepair::isDecrease)
            .map { it as Recognition.Matched }.distinctBy { it.command.action to it.command.parameters }
        if(repaired.size==1) { source="speech-decrease-repair"; return repaired.single() }
        return recognizeExact(raw)
    }
    private fun recognizeExact(raw:String):Recognition {
        val text=key(raw)
        source="grammar-2.1.1"
        // Retain user-defined saved destinations, which may change after this parser was created.
        RahaContacts.target(raw)?.let { return Recognition.Matched(VehicleCommand("CALL_CONTACT","CALL_CONTACT",mapOf("contact_query" to it),"تماس با $it")) }
        OldRahaExtensions.recognize(text)?.let { source="new-command-grammar"; return it }
        val result=grammar.recognize(com.abrar.raha.intent.RahaWordPiece.normalize(raw))
        if(result !is Recognition.Matched) return result
        val c=result.command
        return when(c.action) {
            "SET_FRONT_DEFROSTER", "SET_REAR_DEFROSTER" -> Recognition.Matched(c.copy(action="SET_DEFROSTER",catalogId="SET_DEFROSTER",parameters=c.parameters+mapOf("window" to if(c.action=="SET_FRONT_DEFROSTER") "front" else "rear")))
            else -> result
        }
    }
    @Synchronized override fun close() { lessons=null }
    companion object {
        private fun decode(s:String)=String(Base64.getDecoder().decode(s),Charsets.UTF_8)
        fun key(raw:String):String = raw.lowercase().replace('ي','ی').replace('ك','ک').replace('ۀ','ه')
            .map { when(it) { in '۰'..'۹' -> '0'+(it-'۰'); in '٠'..'٩' -> '0'+(it-'٠'); else -> it } }.joinToString("")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"),"")
            .replace(Regex("[\\u200c\\u200e\\u200f،,!؛:؟?]+")," ").replace(Regex("\\s+")," ").trim()
    }
}
