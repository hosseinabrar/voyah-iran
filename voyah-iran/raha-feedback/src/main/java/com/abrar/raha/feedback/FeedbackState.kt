package com.abrar.raha.feedback

enum class FeedbackState { HIDDEN, WAITING, LISTENING, PROCESSING, SUCCESS, FAILURE, UNRECOGNIZED }

/** Monotonic, deterministic timing; independent of overlays, microphone and audio playback. */
class FeedbackStateMachine {
    var state = FeedbackState.HIDDEN; private set
    private var readyAt = Long.MAX_VALUE
    private var hideAt = Long.MAX_VALUE
    private var commandStarted = false
    private var lastVoiceAt = Long.MIN_VALUE / 2
    fun begin(now: Long, delayedReady: Boolean = true) {
        if (state == FeedbackState.WAITING || state == FeedbackState.LISTENING) return
        state = FeedbackState.WAITING; commandStarted = false
        readyAt = if(delayedReady) now + 2000 else Long.MAX_VALUE; hideAt = Long.MAX_VALUE; lastVoiceAt = now
    }
    fun text(now: Long, text: String) {
        if (text.isBlank()) return
        begin(now)
        commandStarted = true; readyAt = Long.MAX_VALUE; state = FeedbackState.LISTENING
    }
    fun speech(now: Long, active: Boolean) {
        if (active) lastVoiceAt = now
    }
    fun processing() { state = FeedbackState.PROCESSING; readyAt = Long.MAX_VALUE; hideAt = Long.MAX_VALUE }
    fun result(now: Long, success: Boolean) {
        state = if (success) FeedbackState.SUCCESS else FeedbackState.FAILURE
        readyAt = Long.MAX_VALUE; hideAt = now + 1300
    }
    fun unrecognized(now:Long) {
        state=FeedbackState.UNRECOGNIZED
        readyAt=Long.MAX_VALUE;hideAt=now+1300
    }
    fun tick(now: Long): Boolean {
        if (now >= hideAt) cancel()
        if (state == FeedbackState.WAITING && !commandStarted && now >= readyAt && now - lastVoiceAt >= 350) {
            readyAt = Long.MAX_VALUE
            return true
        }
        return false
    }
    fun cancel() { state = FeedbackState.HIDDEN; readyAt = Long.MAX_VALUE; hideAt = Long.MAX_VALUE }
}
