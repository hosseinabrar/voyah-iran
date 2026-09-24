package com.abrar.voyahiran.feature.navigation

/** One attempt per foreground visit. A manual exit must never trigger another click. */
internal class NeshanEntrySession {
    var inside = false
        private set
    var consumed = false
        private set
    var enteredAt = 0L
        private set
    fun enter(now: Long) {
        if (inside) return
        inside = true
        consumed = false
        enteredAt = now
    }
    fun leave() { inside = false; consumed = false }
    fun consume() { consumed = true }
    fun canAttempt(now: Long) = inside && !consumed && now - enteredAt < 20_000L
}
