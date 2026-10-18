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
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){changed()}}
    fun start(){
        if(job!=null)return
        val filter=IntentFilter("theme_loaded")
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED) else context.registerReceiver(receiver,filter)
        prefs.forEach{it.registerOnSharedPreferenceChangeListener(listener)}
        job=scope.launch {
            launch{(context.applicationContext as VoyahIranApplication).subscriptions.vipState.collect{changed()}}
            while(isActive){delay(30_000);changed()}
        }
    }
    fun stop(){if(job==null)return;job?.cancel();job=null;context.unregisterReceiver(receiver);prefs.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)}}
}
