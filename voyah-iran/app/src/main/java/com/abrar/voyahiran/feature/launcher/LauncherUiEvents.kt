package com.abrar.voyahiran.feature.launcher

import android.content.*
import android.os.Build
import com.abrar.voyahiran.VoyahIranApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Theme/preferences/entitlement events, with a slow recovery check for missed OEM broadcasts. */
internal class LauncherUiEvents(private val context:Context,private val scope:CoroutineScope,private val changed:()->Unit){
    private var job:Job?=null
    private val prefs=listOf("launcher","media_controls","display_media_map").map{context.getSharedPreferences(it,0)}
    private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->changed()}
    private var unobserveTheme:(()->Unit)?=null
    fun start(){
        if(job!=null)return
        unobserveTheme=LauncherTheme.observe(context,changed)
        prefs.forEach{it.registerOnSharedPreferenceChangeListener(listener)}
        job=scope.launch {
            launch{(context.applicationContext as VoyahIranApplication).subscriptions.vipState.collect{changed()}}
            while(isActive){delay(30_000);changed()}
        }
    }
    fun stop(){if(job==null)return;job?.cancel();job=null;unobserveTheme?.invoke();unobserveTheme=null;prefs.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)}}
}
