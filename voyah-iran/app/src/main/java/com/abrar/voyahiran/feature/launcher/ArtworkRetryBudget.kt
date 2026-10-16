package com.abrar.voyahiran.feature.launcher

/** Per-track budget, independent of playback/position callbacks. Caller serializes access. */
internal class ArtworkRetryBudget {
    var attempts=0;private set
    private var lastAttempt=0L
    fun claim(now:Long):Boolean {
        if(attempts>=3 || attempts>0 && now-lastAttempt<5000)return false
        attempts++;lastAttempt=now;return true
    }
    val exhausted get()=attempts>=3
}
