package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/** Single-cover cache, bounded I/O and sampled decoding; never downloads on the UI thread. */
class LauncherArtwork(private val context: Context) {
    private var key = ""
    private var bitmap: Bitmap? = null
    fun load(uri: String): Bitmap? {
        if(uri == key) return bitmap
        key=uri; bitmap=null
        if(uri.isBlank())return null
        bitmap=runCatching {
            val parsed=Uri.parse(uri)
            val bytes = when(parsed.scheme) {
                "http", "https" -> {
                    val c=URL(uri).openConnection() as HttpURLConnection
                    c.connectTimeout=2500;c.readTimeout=2500;c.instanceFollowRedirects=false
                    try { check(c.responseCode==200);c.inputStream.use{it.readBytesBounded()} } finally { c.disconnect() }
                }
                "content", "android.resource", "file" -> context.contentResolver.openInputStream(parsed)?.use{it.readBytesBounded()}
                else -> null
            } ?: return@runCatching null
            val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            check(options.outWidth>0 && options.outHeight>0)
            options.inJustDecodeBounds=false
            while(options.outWidth/options.inSampleSize.coerceAtLeast(1)>512 || options.outHeight/options.inSampleSize.coerceAtLeast(1)>512) options.inSampleSize=options.inSampleSize.coerceAtLeast(1)*2
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        }.getOrNull()
        return bitmap
    }
    private fun java.io.InputStream.readBytesBounded():ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=read(buffer);if(n<0)break;check(output.size()+n<=2*1024*1024);output.write(buffer,0,n)}
        return output.toByteArray()
    }
}
