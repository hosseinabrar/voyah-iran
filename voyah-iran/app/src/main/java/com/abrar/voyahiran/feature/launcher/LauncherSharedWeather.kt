package com.abrar.voyahiran.feature.launcher

import android.content.Context
import kotlinx.coroutines.*

/** Main-thread reference-counted weather source: two screens still make one network request. */
internal class LauncherSharedWeather(private val context:Context,private val changed:(LauncherWeatherState)->Unit) {
    private var started=false
    fun start() {
        if(started)return
        started=true;listeners+=changed;changed(snapshot)
        if(source==null)source=LauncherWeather(context.applicationContext,scope) { state ->
            if(snapshot!=state){snapshot=state;listeners.toList().forEach {it(state)}}
        }
        if(listeners.size==1)source!!.start()
    }
    fun stop() {if(!started)return;started=false;listeners-=changed;if(listeners.isEmpty())source?.stop()}
    companion object {
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        private var source:LauncherWeather?=null
        private var snapshot=LauncherWeatherState()
        private val listeners=linkedSetOf<(LauncherWeatherState)->Unit>()
    }
}
