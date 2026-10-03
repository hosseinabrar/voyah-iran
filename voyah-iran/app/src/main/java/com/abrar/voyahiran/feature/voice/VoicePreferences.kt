package com.abrar.voyahiran.feature.voice

import android.content.Context

class VoicePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("raha", Context.MODE_PRIVATE)
    var minimumWakeLevel: Int
        get() = prefs.getInt("minimumWakeLevel", 0).coerceIn(0, 100)
        set(value) { prefs.edit().putInt("minimumWakeLevel", value.coerceIn(0, 100)).apply() }
    var callsEnabled: Boolean
        get() = prefs.getBoolean("callsEnabled", false)
        set(value) { check(prefs.edit().putBoolean("callsEnabled", value).commit()) }
    var showTranscript: Boolean
        get() = prefs.getBoolean("showTranscript", false)
        set(value) { check(prefs.edit().putBoolean("showTranscript", value).commit()) }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { check(prefs.edit().putBoolean("enabled", value).commit()) }
    var phrases: Set<String>
        get() = prefs.getStringSet("phrases", emptySet()).orEmpty().intersect(PHRASES.toSet())
        set(value) { check(prefs.edit().putStringSet("phrases", value.intersect(PHRASES.toSet())).commit()) }
    companion object { val PHRASES = listOf("هی رها", "سلام رها", "رها") }
}

/** QGKeyEvent SHORT (8192) is the same edge used by the OEM IVOKA_START sink. */
class VoiceButtonPolicy(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var last: Long? = null
    @Synchronized fun trigger(action: Int): Boolean {
        if (action != 0x2000) return false
        val now = clock()
        if (last?.let { now - it < 350 } == true) return false
        last = now
        return true
    }
    @Synchronized fun reset() { last = null }
}
