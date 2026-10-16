package com.abrar.voyahiran.core.logging

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Diagnostic logs contain feature state, never provider payloads, accounts, images or typed text. */
object AppLog {
    private val sequence = AtomicLong()
    private val states = ConcurrentHashMap<String, String>()
    fun nextId() = sequence.incrementAndGet()
    fun info(section: String, message: String) { Log.i("VI/$section", message) }
    fun changed(section: String, message: String) {
        if (states.put(section, message) != message) info(section, message)
    }
    fun error(section: String, message: String, error: Throwable) {
        val location = error.stackTrace.firstOrNull()?.toString().orEmpty()
        Log.e("VI/$section", "$message exception=${error.javaClass.simpleName} at=$location")
        // Reflection/Binder wrappers otherwise hide the useful exception in vehicle logs.
        var cause=error.cause
        repeat(4){
            val current=cause ?: return
            Log.e("VI/$section","cause=${current.javaClass.name} stack="+
                current.stackTrace.take(8).joinToString(" <- "))
            cause=current.cause.takeUnless{it===current}
        }
    }
}
