package com.abrar.voyahiran.feature.launcher

/** Retry only the bridge's explicit pre-dispatch rejection, never ambiguous failures. */
internal object MapTransferRetryPolicy {
    fun shouldRetry(exitCode:Int,timedOut:Boolean,output:String,attempt:Int,elapsedMs:Long):Boolean =
        !timedOut && exitCode==75 && output.trim()=="Bridge busy; no command executed" &&
            attempt in 1..7 && elapsedMs in 0..1849
}
