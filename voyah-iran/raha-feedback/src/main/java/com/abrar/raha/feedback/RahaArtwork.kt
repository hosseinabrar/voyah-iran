package com.abrar.raha.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Immutable, shared art decoded off the UI thread, atomically published before any state draws. */
object RahaArtwork {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val waiters=WeakHashMap<View,Boolean>()
    @Volatile private var images:Map<String,Bitmap> = emptyMap()
    private var loading=false
    val ready get()=images.isNotEmpty()
    fun get(name:String)=images[name]
    @Synchronized fun warm(context:Context,view:View?=null){
        if(ready)return
        if(view!=null)waiters[view]=true
        if(loading)return
        loading=true
        val assets=context.applicationContext.assets
        worker.execute {
            val loaded=mutableMapOf<String,Bitmap>()
            val result=runCatching {
                for(theme in listOf("dark","light"))for(state in listOf("normal","success","failure","unknown","processing")){
                    val key="$state-$theme"
                    loaded[key]=assets.open("raha/baked/$key.png").use{requireNotNull(BitmapFactory.decodeStream(it))}
                }
            }
            main.post { synchronized(this) {
                loading=false
                if(result.isSuccess)images=loaded.toMap()
                else {loaded.values.forEach{it.recycle()};Log.e("RahaArtwork","Cannot load feedback artwork",result.exceptionOrNull())}
                waiters.keys.toList().forEach{it.invalidate()};waiters.clear()
            } }
        }
    }
}
