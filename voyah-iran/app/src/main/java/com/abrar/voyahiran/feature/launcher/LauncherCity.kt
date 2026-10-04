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
    private var lastError = ""
    // One bounded worker: a hung OEM geocoder cannot accumulate threads or queued requests.
    private val geocoderWorker = ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>(),
        { task -> Thread(task,"launcher-city").apply { isDaemon=true } },ThreadPoolExecutor.AbortPolicy())
    fun resolve(context: Context, latitude: Double, longitude: Double): String =
        resolveUsing({ systemCity(context,latitude,longitude) }, { lookup(latitude,longitude) })
    internal fun resolveUsing(system: () -> String?, neshan: () -> String?): String =
        runCatching { system() }.getOrNull()?.takeIf {it.isNotBlank()}
            ?: runCatching { neshan() }.getOrNull()?.takeIf {it.isNotBlank()} ?: "Unknown"
    @Suppress("DEPRECATION")
    private fun systemCity(context: Context, latitude: Double, longitude: Double): String? {
        if(!Geocoder.isPresent()) return null
        val task=runCatching { geocoderWorker.submit(Callable {
            Geocoder(context.applicationContext,Locale.ENGLISH).getFromLocation(latitude,longitude,1)
                ?.firstOrNull()?.locality?.trim()?.takeIf { it.isNotBlank() }?.let(::englishName)
        }) }.getOrNull() ?: return null
        return try {task.get(5,TimeUnit.SECONDS)} catch(e:Exception) {task.cancel(true);null}
    }
    fun lookup(latitude: Double, longitude: Double): String? {
        if(BuildConfig.NESHAN_SEARCH_KEY.isBlank()) return null
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
            val city=cityName(JSONObject(raw))
            lastError=""
            city
        } catch(e:Exception) {
            val reason=if(e is IllegalStateException) e.message.orEmpty() else e.javaClass.simpleName
            if(reason!=lastError) {lastError=reason;AppLog.info("LauncherWeather","Neshan city lookup: $reason")}
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
