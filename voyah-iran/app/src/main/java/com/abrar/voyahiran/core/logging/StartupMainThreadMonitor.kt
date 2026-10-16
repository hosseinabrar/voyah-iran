package com.abrar.voyahiran.core.logging

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Bounded startup-only watchdog. Captures code locations, never UI text or user payloads. */
internal object StartupMainThreadMonitor {
    private val started=AtomicBoolean()
    fun start(){
        if(!started.compareAndSet(false,true))return
        val main=Handler(Looper.getMainLooper())
        val pending=AtomicLong(0)
        Thread({
            val end=SystemClock.uptimeMillis()+120_000
            var reported=false
            while(SystemClock.uptimeMillis()<end){
                val now=SystemClock.uptimeMillis()
                if(pending.compareAndSet(0,now))main.post {
                    val since=pending.getAndSet(0)
                    if(since>0 && SystemClock.uptimeMillis()-since>=2000)
                        AppLog.info("StartupMain","main recovered delayMs=${SystemClock.uptimeMillis()-since}")
                }
                val since=pending.get()
                if(since>0 && now-since>=2000 && !reported){
                    reported=true
                    AppLog.info("StartupMain","blocked delayMs=${now-since} stack="+
                        Looper.getMainLooper().thread.stackTrace.take(24).joinToString(" <- "))
                }
                if(since==0L || now-since<2000)reported=false
                try{Thread.sleep(1000)}catch(_:InterruptedException){break}
            }
        },"VI-StartupMain").apply{isDaemon=true;start()}
    }
}
