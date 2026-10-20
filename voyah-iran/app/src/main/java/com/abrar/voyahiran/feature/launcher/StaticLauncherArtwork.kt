package com.abrar.voyahiran.feature.launcher

import android.graphics.*
import android.view.View

/** Immutable, offline-composed artwork. Decode on the shared worker, retain per view. */
internal class StaticLauncherArtwork(private val owner:View) {
    private val images=HashMap<String,Bitmap>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun install(loaded:Map<String,Bitmap>){loaded.filterKeys{it.startsWith("passenger/baked/")}.forEach{(path,b)->images[path.substringAfterLast('/').removeSuffix(".png")]=b}}
    fun draw(c:Canvas,name:String,rect:RectF,alpha:Int=255) {
        val b=images[name] ?: PassengerArtworkCache.get(owner.context,"passenger/baked/$name.png",owner)?.also{images[name]=it} ?: return
        paint.alpha=alpha
        c.drawBitmap(b,null,rect,paint)
    }
}
