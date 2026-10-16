package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import com.abrar.voyahiran.core.logging.AppLog

/** Single-cover cache, bounded I/O and sampled decoding; never downloads on the UI thread. */
class LauncherArtwork(private val context: Context) {
    private var key = ""
    private var bitmap: Bitmap? = null
    private var budget=ArtworkRetryBudget()
    suspend fun loadWithRetry(uri:String,identity:String):Bitmap? {
        repeat(3){attempt->
            currentCoroutineContext().ensureActive()
            val image=withContext(Dispatchers.IO){load(uri,identity)}
            if(image!=null)return image
            if(synchronized(this){key==identity && budget.exhausted})return null
            if(attempt<2)delay(5500)
        }
        return null
    }
    @Synchronized
    fun load(uri: String,identity:String=uri): Bitmap? {
        val now=android.os.SystemClock.elapsedRealtime()
        if(identity!=key){key=identity;bitmap=null;budget=ArtworkRetryBudget()}
        if(bitmap!=null)return bitmap
        if(uri.isBlank())return null
        if(!budget.claim(now))return null
        var stage="open"
        bitmap=runCatching {
            val parsed=Uri.parse(uri)
            val bytes = when(parsed.scheme) {
                "http", "https" -> {
                    val c=URL(uri).openConnection() as HttpURLConnection
                    c.connectTimeout=2500;c.readTimeout=2500;c.instanceFollowRedirects=false
                    try { val code=c.responseCode;stage="http-$code";check(code==200);c.inputStream.use{it.readBytesBounded()} } finally { c.disconnect() }
                }
                "content", "android.resource", "file" -> context.contentResolver.openInputStream(parsed)?.use{it.readBytesBounded()}
                else -> error("Unsupported artwork scheme")
            } ?: error("Artwork stream unavailable")
            stage="decode"
            val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            check(options.outWidth>0 && options.outHeight>0)
            options.inJustDecodeBounds=false
            while(options.outWidth/options.inSampleSize.coerceAtLeast(1)>512 || options.outHeight/options.inSampleSize.coerceAtLeast(1)>512) options.inSampleSize=options.inSampleSize.coerceAtLeast(1)*2
            checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)){"Artwork decode failed"}
        }.onFailure {
            // Never print the URI: providers and signed URLs may contain private tokens.
            AppLog.error("LauncherArtwork","load failed scheme=${Uri.parse(uri).scheme} stage=$stage attempt=${budget.attempts}",it)
        }.getOrNull()
        if(bitmap!=null)AppLog.info("LauncherArtwork","loaded ${bitmap!!.width}x${bitmap!!.height} attempt=${budget.attempts}")
        return bitmap
    }
    private fun java.io.InputStream.readBytesBounded():ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=read(buffer);if(n<0)break;check(output.size()+n<=2*1024*1024);output.write(buffer,0,n)}
        return output.toByteArray()
    }
}
