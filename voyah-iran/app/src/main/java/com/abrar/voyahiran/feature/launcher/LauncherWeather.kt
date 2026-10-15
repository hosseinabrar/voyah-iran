package com.abrar.voyahiran.feature.launcher

import android.annotation.SuppressLint
import com.abrar.voyahiran.core.logging.AppLog
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class LauncherWeatherHour(val time:Long,val temperature:Double,val code:Int,val day:Boolean)
data class LauncherWeatherState(val city: String = "Weather", val temperature: Double? = null,
    val low: Double? = null, val high: Double? = null, val code: Int = -1,
    val day: Boolean = true, val notice: String = "Unknown",val hours:List<LauncherWeatherHour> = emptyList())

internal class LauncherWeather(private val context: Context, private val scope: CoroutineScope,
    private val update: (LauncherWeatherState) -> Unit) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val prefs = context.getSharedPreferences("launcher_weather", Context.MODE_PRIVATE)
    private var location: Location? = null
    private var job: Job? = null
    private var attempted = -300_000L
    private var startupPending = true
    private var failed = false
    private var lastStatus = ""
    private var cached = runCatching { JSONObject(prefs.getString("response", "")!!) }.getOrNull()

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        AppLog.info("LauncherWeather", "start cachedSaved=${cached?.optLong("saved") ?: 0}")
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            runCatching {
                if(provider !in manager.allProviders) return@runCatching
                manager.getLastKnownLocation(provider)?.let(::onLocationChanged)
                // Keep the subscription when GPS is disabled during boot; delivery resumes when enabled.
                manager.requestLocationUpdates(provider, 60_000L, 0f, this, Looper.getMainLooper())
                AppLog.info("LauncherWeather", "subscribed provider=$provider enabled=${manager.isProviderEnabled(provider)}")
            }.onFailure { AppLog.info("LauncherWeather", "location subscription failed provider=$provider ${it.javaClass.simpleName}: ${it.message}") }
        }
        job = scope.launch {
            while (isActive) {
                val fix = location?.takeIf { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..3_600_000_000_000L }
                val status=if(fix!=null) "location ready provider=${fix.provider}" else if(location==null) "waiting for location; no forecast request" else "waiting for fresh location; no forecast request"
                if(status!=lastStatus){lastStatus=status;AppLog.info("LauncherWeather",status)}
                val now = System.currentTimeMillis()
                val old = cached
                // Publish the saved response before waiting for GPS/network, including on cold start.
                update(displayState(old, now, startupPending || failed || fix == null))
                val known = displayState(old, now, false).let { it.temperature != null && LauncherWeatherCode.description(it.code) != "Unknown" }
                val urgent = startupPending || failed || !known
                if (fix != null && (urgent || now-(old?.optLong("saved") ?: 0) >= REFRESH_INTERVAL_MS) &&
                    SystemClock.elapsedRealtime()-attempted >= retryInterval(urgent)) {
                    attempted = SystemClock.elapsedRealtime()
                    AppLog.info("LauncherWeather", "fetch start urgent=$urgent")
                    val result = withContext(Dispatchers.IO) {
                        try { fetch(fix) } catch(e: Exception) {
                            if(e is CancellationException) throw e
                            AppLog.info("LauncherWeather", "fetch failed ${e.javaClass.simpleName}: ${e.message}; retry in 10s")
                            null
                        }
                    }
                    ensureActive()
                    // Retry delays start after completion, so slow failures never produce a tight loop.
                    attempted = SystemClock.elapsedRealtime()
                    failed = result == null
                    if (result != null) {
                        AppLog.info("LauncherWeather", "fetch success saved=${result.optLong("saved")} city=${result.optString("city")}")
                        cached=result
                        startupPending=false
                        prefs.edit().putString("response", result.toString()).apply()
                    }
                    update(displayState(cached, System.currentTimeMillis(), startupPending || failed))
                }
                delay(5_000)
            }
        }
    }
    fun stop() { job?.cancel(); job=null; runCatching { manager.removeUpdates(this) } }
    override fun onLocationChanged(value: Location) {
        if(value.latitude.isFinite() && value.longitude.isFinite() && value.latitude in -90.0..90.0 && value.longitude in -180.0..180.0 &&
            (location == null || value.elapsedRealtimeNanos > location!!.elapsedRealtimeNanos)) location=value
    }
    override fun onProviderEnabled(provider: String) { AppLog.info("LauncherWeather", "provider enabled=$provider") }
    override fun onProviderDisabled(provider: String) { AppLog.info("LauncherWeather", "provider disabled=$provider") }
    @Deprecated("Legacy Android location callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @Suppress("DEPRECATION")
    private fun fetch(fix: Location): JSONObject {
        val url="https://api.open-meteo.com/v1/forecast?latitude=${fix.latitude}&longitude=${fix.longitude}"+
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&hourly=temperature_2m,weather_code,is_day&timeformat=unixtime&timezone=auto&forecast_days=2"
        val connection=URL(url).openConnection() as HttpURLConnection
        val data=try {
            connection.connectTimeout=8000; connection.readTimeout=8000
            check(connection.responseCode == 200) { "Open-Meteo HTTP ${connection.responseCode}" }
            val raw=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(4096)
                while(true) { val n=input.read(buffer); if(n<0)break; check(output.size()+n<=131072); output.write(buffer,0,n) }
                output.toByteArray()
            }
            check(raw.size<=131072)
            JSONObject(String(raw,Charsets.UTF_8)).also {
                check(it.getJSONObject("current").getDouble("temperature_2m").isFinite())
                check(it.getJSONObject("daily").getJSONArray("temperature_2m_max").getDouble(0).isFinite())
                check(it.getJSONObject("daily").getJSONArray("temperature_2m_min").getDouble(0).isFinite())
            }
        } finally {connection.disconnect()}
        val city=LauncherCity.resolve(fix.latitude,fix.longitude)
        return data.put("city",city).put("lat",fix.latitude).put("lon",fix.longitude).put("saved",System.currentTimeMillis())
    }
    companion object {
        internal const val REFRESH_INTERVAL_MS = 300_000L
        internal fun retryInterval(urgent: Boolean): Long = if(urgent) 10_000L else 300_000L
        internal fun displayState(data: JSONObject?, now: Long, saved: Boolean): LauncherWeatherState {
            if(data == null || now-data.optLong("saved") !in 0 until 86_400_000L) return LauncherWeatherState()
            return runCatching { parse(data, now, saved).also {
                check(it.temperature?.isFinite()==true && it.low?.isFinite()==true && it.high?.isFinite()==true)
            } }.getOrDefault(LauncherWeatherState())
        }
        internal fun parse(data: JSONObject, now: Long, noFix: Boolean): LauncherWeatherState {
            val current=data.getJSONObject("current"); val daily=data.getJSONObject("daily")
            val stale=now-data.optLong("saved")>=REFRESH_INTERVAL_MS
            val notice=if(stale || noFix) "Saved · "+java.text.SimpleDateFormat("HH:mm",Locale.US).format(java.util.Date(data.optLong("saved"))) else ""
            return LauncherWeatherState(data.optString("city","Weather"),current.getDouble("temperature_2m"),
                daily.getJSONArray("temperature_2m_min").getDouble(0), daily.getJSONArray("temperature_2m_max").getDouble(0),
                current.optInt("weather_code",-1),current.optInt("is_day",1)==1,notice,parseHours(data,now))
        }
        internal fun parseHours(data:JSONObject,now:Long):List<LauncherWeatherHour> {
            val h=data.optJSONObject("hourly") ?: return emptyList()
            val times=h.optJSONArray("time") ?: return emptyList()
            return (0 until times.length()).mapNotNull {i->runCatching {
                val time=times.getLong(i)*1000
                if(time<=now)return@runCatching null
                val t=h.getJSONArray("temperature_2m").getDouble(i);check(t.isFinite())
                LauncherWeatherHour(time,t,h.getJSONArray("weather_code").getInt(i),h.getJSONArray("is_day").getInt(i)==1)
            }.getOrNull()}.take(5)
        }
    }
}
