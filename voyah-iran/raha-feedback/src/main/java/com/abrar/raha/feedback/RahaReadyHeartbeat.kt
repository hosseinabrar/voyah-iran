package com.abrar.raha.feedback

/** A readiness cue, never a silence animation after the command has begun. */
class RahaReadyHeartbeat {
    var allowed=true; private set
    fun begin(){allowed=true}
    fun text(value:String){
        val words=value.lowercase().replace(Regex("[\\u064B-\\u065F\\u0670\\u200C\\u200D]"),"")
            .split(Regex("[^\\p{L}\\p{N}]+"))
        if(words.any{it.isNotBlank() && it!="رها" && it!="raha"})allowed=false
    }
}
