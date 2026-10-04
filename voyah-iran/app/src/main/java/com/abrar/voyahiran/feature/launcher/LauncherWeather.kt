package com.abrar.voyahiran.feature.launcher

import android.annotation.SuppressLint
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

data class LauncherWeatherState(val city: String = "Weather", val temperature: Double? = null,
    val low: Double? = null, val high: Double? = null, val code: Int = -1,
    val day: Boolean = true, val notice: String = "Unknown")

internal class LauncherWeather(private val context: Context, private val scope: CoroutineScope,
    private val update: (LauncherWeatherState) -> Unit) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val prefs = context.getSharedPreferences("launcher_weather", Context.MODE_PRIVATE)
    private var location: Location? = null
    private var job: Job? = null
    private var attempted = -300_000L
    private var startupPending = true
    private var failed = false
    private var cached = runCatching { JSONObject(prefs.getString("response", "")!!) }.getOrNull()

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                manager.getLastKnownLocation(provider)?.let(::onLocationChanged)
                if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, 60_000L, 0f, this, Looper.getMainLooper())
            }
        }
        job = scope.launch {
            while (isActive) {
                val fix = location?.takeIf { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..3_600_000_000_000L }
                val now = System.currentTimeMillis()
                val old = cached
                // Publish the saved response before waiting for GPS/network, including on cold start.
                update(displayState(old, now, startupPending || failed || fix == null))
                val known = displayState(old, now, false).let { it.temperature != null && LauncherWeatherCode.description(it.code) != "Unknown" }
                val urgent = startupPending || failed || !known
                if (fix != null && (urgent || now-(old?.optLong("saved") ?: 0) >= REFRESH_INTERVAL_MS) &&
                    SystemClock.elapsedRealtime()-attempted >= retryInterval(urgent)) {
                    attempted = SystemClock.elapsedRealtime()
                    val result = withContext(Dispatchers.IO) { runCatching { fetch(fix) }.getOrNull() }
                    ensureActive()
                    // Retry delays start after completion, so slow failures never produce a tight loop.
                    attempted = SystemClock.elapsedRealtime()
                    failed = result == null
                    if (result != null) {
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
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Legacy Android location callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @Suppress("DEPRECATION")
    private fun fetch(fix: Location): JSONObject {
        val url="https://api.open-meteo.com/v1/forecast?latitude=${fix.latitude}&longitude=${fix.longitude}"+
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val connection=URL(url).openConnection() as HttpURLConnection
        val data=try {
            connection.connectTimeout=8000; connection.readTimeout=8000
            check(connection.responseCode == 200)
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
        val city=LauncherCity.resolve(context,fix.latitude,fix.longitude)
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
                current.optInt("weather_code",-1),current.optInt("is_day",1)==1,notice)
        }
    }
}
