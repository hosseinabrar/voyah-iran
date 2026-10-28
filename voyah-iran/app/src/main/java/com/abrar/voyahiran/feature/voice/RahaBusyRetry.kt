package com.abrar.voyahiran.feature.voice

import com.abrar.voyahiran.core.shell.CommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Retry admission, never execution: the authenticated bridge explicitly guarantees no dispatch.
 * Relative adjustments, toggles and partially completed plans must never be replayed on other errors.
 * Five seconds is the admission window, not a timeout for an operation already accepted by the ECU.
 */
internal object RahaBusyRetry {
    suspend fun run(
        now:()->Long,
        allowed:()->Boolean,
        wait:suspend (Long)->Unit = { kotlinx.coroutines.delay(it) },
        onRetry:(Int,Long)->Unit = { _,_-> },
        execute:suspend ()->CommandResult,
    ):CommandResult {
        val started=now()
        var retries=0
        fun permitted() {if(!allowed())throw CancellationException("Raha command cancelled before bridge admission")}
        while(true) {
            currentCoroutineContext().ensureActive();permitted()
            val result=execute()
            if(result.exitCode!=75 || result.timedOut || result.output.trim()!="Bridge busy; no command executed")return result
            val elapsed=now()-started
            if(retries>=10 || elapsed+500>5000)return result
            wait(500)
            currentCoroutineContext().ensureActive();permitted()
            if(now()-started>5000)return result
            retries++
            onRetry(retries,now()-started)
        }
    }
}
