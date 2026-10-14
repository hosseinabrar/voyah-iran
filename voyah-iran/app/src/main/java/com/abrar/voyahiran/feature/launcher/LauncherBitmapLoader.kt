package com.abrar.voyahiran.feature.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*

/** Main-thread cache, background decoding. Missing artwork never holds up a card's first frame. */
internal class LauncherBitmapLoader(private val resources: Resources, private val changed: () -> Unit) {
    private val blank=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
    private val images=object:android.util.LruCache<Int,Bitmap>(32*1024*1024) {
        override fun sizeOf(key:Int,value:Bitmap)=value.byteCount
    }
    private val pending=mutableSetOf<Int>()
    private var scope: CoroutineScope?=null
    fun start() { if(scope==null)scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate) }
    fun stop() { scope?.cancel();scope=null;pending.clear() }
    fun get(id:Int, weather:Boolean=false, sample:Int=1):Bitmap {
        images.get(id)?.let {return it}
        val active=scope ?: return blank
        if(pending.add(id))active.launch {
            val bitmap=withContext(Dispatchers.IO) {
                runCatching {
                    if(weather)LauncherWeatherArtwork.decode(resources,id)
                    else BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply {inSampleSize=sample})
                }.getOrNull()
            }
            // Cache a failed decode too; do not retry a corrupt/missing resource every frame.
            images.put(id,bitmap ?: blank)
            pending.remove(id)
            changed()
        }
        return blank
    }
}
