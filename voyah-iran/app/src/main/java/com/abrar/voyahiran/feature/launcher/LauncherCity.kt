package com.abrar.voyahiran.feature.launcher

import android.content.Context
import android.location.Geocoder
import java.util.concurrent.*
import com.abrar.voyahiran.BuildConfig
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.navigation.ClusterLatinText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** System geocoder first, then Neshan; neither failure blocks weather display indefinitely. */
internal object LauncherCity {
    private const val TAG = "LauncherCity"
    // One bounded worker: a hung OEM geocoder cannot accumulate threads or queued requests.
    private val geocoderWorker = ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>(),
        { task -> Thread(task,"launcher-city").apply { isDaemon=true } },ThreadPoolExecutor.AbortPolicy())
    @Synchronized fun resolve(context: Context, latitude: Double, longitude: Double): String {
        val started=android.os.SystemClock.elapsedRealtime()
        AppLog.info(TAG,"lookup started")
        val result=resolveUsing({ systemCity(context,latitude,longitude) }, { lookup(latitude,longitude) })
        AppLog.info(TAG,"lookup complete city=$result elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}")
        return result
    }
    internal fun resolveUsing(system: () -> String?, neshan: () -> String?): String =
        runCatching { system() }.getOrNull()?.takeIf {it.isNotBlank()}
            ?: runCatching { neshan() }.getOrNull()?.takeIf {it.isNotBlank()} ?: "Unknown"
    @Suppress("DEPRECATION")
    private fun systemCity(context: Context, latitude: Double, longitude: Double): String? {
        if(!Geocoder.isPresent()) { AppLog.info(TAG,"Android geocoder unavailable");return null }
        val task=runCatching { geocoderWorker.submit(Callable {
            Geocoder(context.applicationContext,Locale.ENGLISH).getFromLocation(latitude,longitude,1)
                ?.firstOrNull()?.locality?.trim()?.takeIf { it.isNotBlank() }?.let(::englishName)
        }) }.onFailure { AppLog.info(TAG,"Android worker unavailable: ${it.javaClass.simpleName}") }.getOrNull() ?: return null
        return try {task.get(5,TimeUnit.SECONDS).also { AppLog.info(TAG,"Android result=${it ?: "empty locality"}; fallback=${it.isNullOrBlank()}") }}
        catch(e:Exception) {task.cancel(true);AppLog.info(TAG,"Android failed: ${(e.cause ?: e).javaClass.simpleName}; trying Neshan");null}
    }
    fun lookup(latitude: Double, longitude: Double): String? {
        if(BuildConfig.NESHAN_SEARCH_KEY.isBlank()) { AppLog.info(TAG,"Neshan skipped: API key missing");return null }
        val connection = URL("https://api.neshan.org/v5/reverse?lat=$latitude&lng=$longitude").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Api-Key",BuildConfig.NESHAN_SEARCH_KEY)
            check(connection.responseCode==200) { "HTTP ${connection.responseCode}" }
            val raw=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) { val n=input.read(buffer);if(n<0)break;check(output.size()+n<=65536);output.write(buffer,0,n) }
                output.toString("UTF-8")
            }
            val data=JSONObject(raw)
            val city=cityName(data)
            AppLog.info(TAG,"Neshan HTTP=200 status=${data.optString("status")} city=${city ?: "empty city/village"}")
            city
        } catch(e:Exception) {
            val reason=if(e is IllegalStateException) e.message.orEmpty() else e.javaClass.simpleName
            AppLog.info(TAG,"Neshan failed: $reason")
            null
        } finally {connection.disconnect()}
    }
    internal fun cityName(data: JSONObject): String? {
        if(data.optString("status")!="OK") return null
        val name=listOf("city","village").firstNotNullOfOrNull {
            data.optString(it).trim().takeIf { value -> value.isNotBlank() && value!="null" }
        } ?: return null
        return englishName(name)
    }
    private fun englishName(name: String): String? {
        if(name.all { it.code < 128 }) return name
        val canonical=mapOf("تهران" to "Tehran", "گرگان" to "Gorgan", "قم" to "Qom", "همدان" to "Hamedan")
        return canonical[name] ?: ClusterLatinText.convertName(name).split(' ').joinToString(" ") {
            it.replaceFirstChar { c -> c.titlecase(Locale.US) }
        }.takeIf {it.isNotBlank()}
    }
}
