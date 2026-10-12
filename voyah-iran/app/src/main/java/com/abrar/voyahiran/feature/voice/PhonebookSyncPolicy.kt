package com.abrar.voyahiran.feature.voice

internal object PhonebookSyncPolicy {
    fun delay(success:Boolean, populated:Boolean):Long =
        if(success && populated) 5*60_000L else 5_000L
}
