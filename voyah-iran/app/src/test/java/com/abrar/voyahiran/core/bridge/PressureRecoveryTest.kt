package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class PressureRecoveryTest {
    private class Timer: ScheduledThreadPoolExecutor(1) {
        val jobs=ArrayList<Job>()
        class Job(command:Runnable):FutureTask<Unit>(command,Unit),ScheduledFuture<Unit> {
            override fun getDelay(unit:TimeUnit)=unit.convert(5,TimeUnit.SECONDS)
            override fun compareTo(other:Delayed)=0
        }
        override fun schedule(command:Runnable,delay:Long,unit:TimeUnit):ScheduledFuture<*> {
            assertEquals(5000L,unit.toMillis(delay))
            return Job(command).also{jobs.add(it)}
        }
        fun tick(){jobs.removeAt(0).run()}
    }
    @Test fun retriesInvalidAndExceptionsThenStopsAtValidValue(){
        val timer=Timer();var reads=0;val results=mutableListOf<Int>()
        val retry=PressureRecovery(timer,{reads++;when(reads){1->null;2->0;3->throw IllegalStateException();else->890}},{results.add(it)})
        retry.observed(-1)
        retry.observed(0);retry.observed(null)
        assertEquals(1,timer.jobs.size)
        repeat(4){timer.tick()}
        assertEquals(listOf(890),results)
        assertTrue(timer.jobs.isEmpty())
        retry.close();timer.shutdownNow()
    }
    @Test fun callbackAndCloseCancelPendingReads(){
        val timer=Timer();var reads=0
        val retry=PressureRecovery(timer,{reads++;900},{fail("unexpected readback")})
        retry.observed(null);retry.observed(890);timer.tick()
        assertEquals(0,reads)
        retry.observed(0);retry.close();timer.tick()
        assertEquals(0,reads);timer.shutdownNow()
    }
    @Test fun callbackDuringReadWins(){
        val timer=Timer()
        lateinit var retry:PressureRecovery
        retry=PressureRecovery(timer,{retry.observed(900);890},{fail("stale result overwrote callback")})
        retry.observed(null);timer.tick()
        assertTrue(timer.jobs.isEmpty());retry.close();timer.shutdownNow()
    }
}
