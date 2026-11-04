package com.abrar.voyahiran.feature.launcher

import android.content.*
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Launcher follows the vehicle theme, independently of third-party app theme overrides. */
internal object LauncherTheme {
    private val propertyReader by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }
    private val main by lazy {Handler(Looper.getMainLooper())}
    private val listeners=linkedSetOf<()->Unit>()
    private var app:Context?=null
    @Volatile private var cached=false
    @Volatile private var observing=false
    private val recovery=object:Runnable{
        override fun run(){refresh();if(listeners.isNotEmpty())main.postDelayed(this,30_000)}
    }
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context?,intent:Intent?){refresh()}
    }
    /** Main-thread, shared OEM observer. The slow recovery poll covers missed broadcasts. */
    fun observe(context:Context,changed:()->Unit):()->Unit{
        if(listeners.isEmpty()){
            cached=read()
            val c=context.applicationContext;app=c
            if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,IntentFilter("theme_loaded"),Context.RECEIVER_EXPORTED)
            else c.registerReceiver(receiver,IntentFilter("theme_loaded"))
            main.postDelayed(recovery,30_000)
        }
        listeners+=changed;observing=true
        return {
            listeners-=changed
            if(listeners.isEmpty()){observing=false;main.removeCallbacks(recovery);app?.unregisterReceiver(receiver);app=null}
        }
    }
    private fun refresh(){
        val next=read()
        if(next!=cached){cached=next;listeners.toList().forEach{it()}}
    }
    fun isLight():Boolean=if(observing)cached else read()
    private fun read(): Boolean {
        val vehicle = runCatching {
            propertyReader?.invoke(null, "persist.sys.qg.currenttheme", "") as? String
        }.getOrNull()
        return resolve(vehicle)
    }
    // Speedy Dash is named "simple" in the OEM property. Unknown themes default to dark.
    internal fun resolve(vehicle: String?): Boolean = vehicle == "simple"
}
