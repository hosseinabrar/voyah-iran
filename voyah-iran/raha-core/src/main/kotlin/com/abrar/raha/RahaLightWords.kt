package com.abrar.raha

/** Spoken/plural lamp forms; qualifiers such as داخل، جلو and کوچک stay intact. */
object RahaLightWords {
    private val forms=Regex("(?<!\\S)چراغ(?: های| ای| ها|های|ها|ای)?(?!\\S)")
    fun normalize(text:String)=forms.replace(RahaInputText.spaces(text),"چراغ")
}
