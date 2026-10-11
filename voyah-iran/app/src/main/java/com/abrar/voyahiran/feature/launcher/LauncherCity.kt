package com.abrar.voyahiran.feature.launcher


import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.feature.navigation.ClusterLatinText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** GPS-based city lookup through BigDataCloud only. */
internal object LauncherCity {
    private var lastError = ""
    fun resolve(latitude: Double, longitude: Double): String =
        runCatching { lookup(latitude,longitude) }.getOrNull() ?: "Unknown"
    fun lookup(latitude: Double, longitude: Double): String? {
        if(!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val connection = URL("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$latitude&longitude=$longitude&localityLanguage=en").openConnection() as HttpURLConnection
        return try {
            // BigDataCloud may redirect to its regional endpoint (HTTP 307).
            // HttpURLConnection follows HTTPS redirects without allowing a protocol downgrade.
            connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=true
            AppLog.info("LauncherWeather","BigDataCloud city lookup: start GPS coordinates, language=en")
            check(connection.responseCode==200) { "HTTP ${connection.responseCode}" }
            val raw=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) { val n=input.read(buffer);if(n<0)break;check(output.size()+n<=65536);output.write(buffer,0,n) }
                output.toString("UTF-8")
            }
            val city=cityName(JSONObject(raw))
            AppLog.info("LauncherWeather","BigDataCloud city lookup: HTTP 200 city=${city ?: "Unknown"}")
            lastError=""
            city
        } catch(e:Exception) {
            val reason=if(e is IllegalStateException) e.message.orEmpty() else e.javaClass.simpleName
            if(reason!=lastError) {lastError=reason;AppLog.info("LauncherWeather","BigDataCloud city lookup: $reason")}
            null
        } finally {connection.disconnect()}
    }
    internal fun cityName(data: JSONObject): String? {
        if(data.has("error") || data.optString("lookupSource").contains("ip",ignoreCase=true)) return null
        val name=listOf("city","locality").firstNotNullOfOrNull {
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
