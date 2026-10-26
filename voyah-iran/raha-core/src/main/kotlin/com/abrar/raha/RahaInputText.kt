package com.abrar.raha

/** Input boundary only: downstream grammar treats ZWNJ exactly like a word space. */
object RahaInputText {
    fun spaces(text:String):String = text.replace('\u200c',' ').replace(Regex("\\s+")," ").trim()
}
