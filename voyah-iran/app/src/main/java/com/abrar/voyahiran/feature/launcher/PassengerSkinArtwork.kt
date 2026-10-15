package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

/** Installed VehicleAir skin first, bundled artwork only when that resource is unavailable. */
internal class PassengerSkinArtwork(private val context:Context) {
    private val cache=object:android.util.LruCache<String,ByteArray>(16*1024*1024){override fun sizeOf(key:String,value:ByteArray)=value.size}
    private val missing=HashSet<String>()
    private var theme:String?=null
    @Synchronized fun clear(){cache.evictAll();missing.clear();theme=null}
    private val oem by lazy {runCatching {
        val info=context.packageManager.getInstalledApplications(0).first {it.sourceDir.endsWith("/VehicleAir-release-signed.apk")}
        context.createPackageContext(info.packageName,Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()}
    @Synchronized fun get(path:String,light:Boolean):ByteArray? {
        val key=light.toString();if(theme!=key){clear();theme=key}
        if(!path.startsWith("oem-x/") && !path.startsWith("oem-ac/"))return null
        val name=if(path.endsWith("cabin-day-fade-v2.png"))"air_bg_h97c" else path.substringAfterLast('/').substringBeforeLast('.')
        if(!name.matches(Regex("[a-z0-9_]+")))return null
        cache.get(name)?.let {return it}
        if(name in missing)return null
        val value=runCatching {
            val c=oem ?: return@runCatching null
            val id=c.resources.getIdentifier(name,"drawable",c.packageName);if(id==0)return@runCatching null
            val t=c.classLoader.loadClass("com.qinggan.theme.SkinResourceManager")
            val manager=t.getMethod("getInstance",Context::class.java).invoke(null,c)
            val d=t.getMethod("getDrawable",Int::class.javaPrimitiveType).invoke(manager,id) as Drawable
            val w=d.intrinsicWidth;val h=d.intrinsicHeight;check(w in 1..2048 && h in 1..2048)
            val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            try {d.setBounds(0,0,w,h);d.draw(Canvas(b));ByteArrayOutputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}
            finally {b.recycle()}
        }.getOrNull()
        if(value!=null)cache.put(name,value) else missing.add(name)
        return value
    }
}
