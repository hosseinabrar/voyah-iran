package com.abrar.voyahiran.feature.remote

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Local headunit -> TBox link; never sent through the public relay. */
object RemoteApi {
    fun call(body: JSONObject): String {
        val c = URL("http://172.16.104.20:18782/local").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 3000
            c.readTimeout = 5000
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            check(c.responseCode == 200) { "TBox remote HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().use { input ->
                val result = StringBuilder()
                val buffer = CharArray(2048)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    result.append(buffer,0,count)
                    check(result.length <= 32768)
                }
                result.toString()
            }
        } finally { c.disconnect() }
    }
}
