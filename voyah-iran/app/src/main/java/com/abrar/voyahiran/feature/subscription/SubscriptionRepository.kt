package com.abrar.voyahiran.feature.subscription

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.R
import com.abrar.voyahiran.BuildConfig
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject

class SubscriptionRejected(val blocked: Boolean) : Exception()
class SubscriptionRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)
    private val verifier by lazy { SubscriptionToken(context.resources.openRawResource(R.raw.subscription_public_key).bufferedReader().use { it.readText() }) }
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun readVin(): String {
        val result = ProcessCommandRunner().run(listOf("su", "0", "subscription-vin", "read"), 5)
        check(result.exitCode == 0 && !result.timedOut) { "Vehicle identity unavailable" }
        return result.output.trim().also { check(it.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) { "Vehicle identity invalid" } }
    }
    fun cached(vin: String): SubscriptionGrant? = runCatching {
        verifier.verify(prefs.getString("token", null) ?: return null, vin)
    }.getOrNull()
    fun blocked() = prefs.getBoolean("blocked", false)
    fun now(): Long = SubscriptionClock.now(prefs.getLong("issued",0),prefs.getLong("wall",0),prefs.getLong("elapsed",0),prefs.getInt("boot",-2),prefs.getLong("floor",0),System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot())
    fun checkpoint() { if(prefs.contains("token")) prefs.edit().putLong("floor", now()).apply() }
    fun renew(vin: String): SubscriptionGrant {
        val url = URL(BuildConfig.SUBSCRIPTION_URL)
        require(url.protocol == "http" && url.host == "voyah-iran.ir" && url.userInfo == null)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000; connection.readTimeout = 15000; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("vin", vin).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if(connection.responseCode == 403) {
                check(prefs.edit().putBoolean("blocked",true).commit())
                throw SubscriptionRejected(true)
            }
            if(connection.responseCode != 200) throw SubscriptionRejected(false)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while(output.size() <= 16384) {
                    val count = input.read(buffer)
                    if(count < 0) break
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            check(bytes.size <= 16384) { "Subscription response too large" }
            val token = JSONObject(String(bytes,Charsets.UTF_8)).getString("token")
            val grant = verifier.verify(token,vin)
            // Trust only signed iat/exp; never trust the unsigned expiresAt/serverTime hints.
            check(prefs.edit().putString("token",token).putLong("issued",grant.issuedAt)
                .putLong("wall",System.currentTimeMillis()).putLong("elapsed",SystemClock.elapsedRealtime())
                .putInt("boot",boot()).putLong("floor",grant.issuedAt).putBoolean("blocked",false).commit())
            return grant
        } finally { connection.disconnect() }
    }
}
