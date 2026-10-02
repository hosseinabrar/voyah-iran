package com.abrar.voyahiran.feature.steering.domain

/** OEM QGKeyEvent wire layouts verified against Android 9 H97A and Android 11 SDKs. */
class QingganKeyCodec(private val extended: Boolean) {
    data class Event<T>(val code: Int, val action: Int, val flags: Int, val payload: T, val display: Int)
    fun <T> read(integer: () -> Int, payload: () -> T): Event<T> {
        val code=integer(); val action=integer()
        val flags=if(extended) integer() else 0
        val data=payload()
        val display=if(extended) integer() else 0
        return Event(code,action,flags,data,display)
    }
    fun <T> write(event: Event<T>, integer: (Int) -> Unit, payload: (T) -> Unit) {
        integer(event.code); integer(event.action)
        if(extended) integer(event.flags)
        payload(event.payload)
        if(extended) integer(event.display)
    }
}
