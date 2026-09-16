package com.abrar.raha

/** Experimental single-name confirmation; legacy remains the vehicle app default. */
enum class WakeSensitivity(val confidence: Double, val stableMs: Long) {
    LEGACY(0.0, 0), RELAXED(0.65, 120), BALANCED(0.80, 280), STRICT(0.90, 500)
}
class RahaWakeConfirmation(private val sensitivity: WakeSensitivity) {
    private var candidateSince: Long? = null
    fun reset() { candidateSince = null }
    fun accept(text: String, confidence: Double?, now: Long, final: Boolean): Boolean {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (sensitivity == WakeSensitivity.LEGACY) return true
        if (normalized != "رها" && !normalized.startsWith("رها ")) {
            reset()
            return true // Existing gate decides whether another selected phrase matches.
        }
        if (confidence == null || !confidence.isFinite() || confidence < sensitivity.confidence) {
            reset(); return false
        }
        val since = candidateSince ?: now.also { candidateSince = it }
        // Final word confidence may confirm without waiting for another silence interval.
        return final || now - since >= sensitivity.stableMs
    }
}
