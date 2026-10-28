package com.abrar.voyahiran.feature.voice

import com.abrar.voyahiran.core.shell.CommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RahaBusyRetryTest {
    private val busy=CommandResult(75,"Bridge busy; no command executed")
    private val ok=CommandResult(0,"DISPATCHED")
    @Test fun successIsNotReplayed()= runBlocking {
        var calls=0
        assertEquals(ok,RahaBusyRetry.run({0},{true},wait={error("no wait")}){calls++;ok})
        assertEquals(1,calls)
    }
    @Test fun busyRetriesAtHalfSecondThenOnlyOneExecution()= runBlocking {
        var time=0L;var calls=0;val starts=mutableListOf<Long>()
        val result=RahaBusyRetry.run({time},{true},wait={time+=it}){starts+=time;calls++;if(calls<4)busy else ok}
        assertEquals(ok,result);assertEquals(listOf(0L,500L,1000L,1500L),starts)
    }
    @Test fun persistentBusyStopsAfterFiveSeconds()= runBlocking {
        var time=0L;var calls=0
        assertEquals(busy,RahaBusyRetry.run({time},{true},wait={time+=it}){calls++;busy})
        assertEquals(11,calls);assertEquals(5000L,time)
    }
    @Test fun failedOrUnknownOutcomeNeverRetries()= runBlocking {
        for(result in listOf(CommandResult(1,"ECU confirmation failed"),CommandResult(124,"response lost",true),CommandResult(127,"unavailable"),CommandResult(75,"other failure"),CommandResult(75,busy.output,true))) {
            var calls=0
            assertEquals(result,RahaBusyRetry.run({0},{true},wait={error("unsafe retry")}){calls++;result})
            assertEquals(1,calls)
        }
    }
    @Test fun cancellationDuringWaitPreventsAnotherDispatch()= runBlocking {
        var time=0L;var allowed=true;var calls=0
        try {RahaBusyRetry.run({time},{allowed},wait={time+=it;allowed=false}){calls++;busy};fail("must cancel")}
        catch(_:CancellationException){};assertEquals(1,calls)
    }
    @Test fun disabledBeforeFirstAttemptNeverDispatches()= runBlocking {
        try {RahaBusyRetry.run({0},{false}){error("must not dispatch")};fail("must cancel")}
        catch(_:CancellationException){}
    }
    @Test fun schedulerDelayCannotExtendAdmissionWindow()= runBlocking {
        var time=0L;var calls=0
        assertEquals(busy,RahaBusyRetry.run({time},{true},wait={time+=6000}){calls++;busy})
        assertEquals(1,calls)
    }
    @Test fun responseLatencyCountsTowardsWindow()= runBlocking {
        var time=0L;var calls=0
        assertEquals(busy,RahaBusyRetry.run({time},{true},wait={time+=it}){calls++;time+=4501;busy})
        assertEquals(1,calls)
    }
}
