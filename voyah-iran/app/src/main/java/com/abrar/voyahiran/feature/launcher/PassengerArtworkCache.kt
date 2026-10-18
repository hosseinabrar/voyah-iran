package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import com.abrar.voyahiran.core.logging.AppLog
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Process-owned immutable artwork, shared across Activity recreation. Never decode in onDraw. */
internal object PassengerArtworkCache {
    private val images=object:LruCache<String,Bitmap>(48*1024*1024){
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val pending=mutableMapOf<String,WeakHashMap<View,Boolean>>()
    private var warming=false
    @Synchronized fun get(context:Context,path:String,view:View?=null):Bitmap? {
        images.get(path)?.let{return it}
        pending[path]?.let{if(view!=null)it[view]=true;return null}
        pending[path]=WeakHashMap<View,Boolean>().apply{if(view!=null)put(view,true)}
        val assets=context.applicationContext.assets
        worker.execute {
            val result=runCatching{assets.open(path).use{requireNotNull(BitmapFactory.decodeStream(it))}}
            main.post {
                synchronized(this) {
                    result.onSuccess{images.put(path,it)}.onFailure{AppLog.error("PassengerArtwork","Decode failed: $path",it)}
                    pending.remove(path)?.keys?.toList()?.forEach{it.invalidate()}
                }
            }
        }
        return null
    }
    @Synchronized fun warm(context:Context) {
        if(warming)return
        warming=true
        val app=context.applicationContext
        worker.execute {
            val paths=runCatching {
                val ac=app.assets.list("passenger/oem-ac").orEmpty().filter{it.endsWith(".png")&&it!="cabin-day-fade-v2.png"&&it!="air_bg_h97c.png"}.map{"passenger/oem-ac/$it"}
                val objects=listOf("object-1","object-2","object-3","object-1-day-v1","object-2-day-v2","object-3-day-v1")
                val fragrance=(objects+objects.map{"$it-gray"})
                    .plus(listOf("shuigan","zhuzi","mudan").flatMap {n->listOf("on","off","off-gray").map {"fragrance_icon_${n}_$it"}})
                    .map{"passenger/fragrance-wide/$it.png"}
                val baked=app.assets.list("passenger/baked").orEmpty().filter{it.endsWith(".png")&&!it.startsWith("air-")}.map{"passenger/baked/$it"}
                baked+ac+fragrance
            }.getOrElse{emptyList()}
            main.post{paths.forEach{get(app,it)}}
        }
    }
}
