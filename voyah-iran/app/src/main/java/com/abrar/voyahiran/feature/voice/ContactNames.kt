package com.abrar.voyahiran.feature.voice

import android.content.Context
import com.abrar.raha.FinglishContactNames

internal class ContactNames(context:Context) {
    private val converter=FinglishContactNames { context.assets.open("contact-names/$it") }
    fun convert(name:String)=converter.convert(name)
}
