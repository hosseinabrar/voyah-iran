package com.abrar.voyahiran.feature.navigation

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

internal class NeshanConfigDownload(private val cache: File,
    private val endpoint: String = "http://voyah-iran.ir/configs/neshan.json",
    private val publicKey: String = NeshanConfigTrust.publicPem) {
    data class Result(val catalog: NeshanConfig.Catalog, val changed: Boolean)
    fun update(): Result {
        val old = runCatching { NeshanConfig.verify(NeshanConfig.read(cache), publicKey) }.getOrNull()
        val metadataFile = File(cache.path + ".http")
        val metadata = runCatching { JSONObject(NeshanConfig.read(metadataFile)) }.getOrNull()
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7000; connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (old != null && metadata?.optString("digest") == old.digest) {
                val etag = metadata.optString("etag")
                val modified = metadata.optString("modified")
                if (etag.isNotEmpty()) connection.setRequestProperty("If-None-Match", etag)
                else if (modified.isNotEmpty()) connection.setRequestProperty("If-Modified-Since", modified)
            }
            if (connection.responseCode == 304) {
                check(old != null) { "304 without verified cache" }
                return Result(old, false)
            }
            check(connection.responseCode == 200) { "Config HTTP ${connection.responseCode}" }
            require(connection.contentLengthLong <= NeshanConfig.MAX_BYTES)
            val text = connection.inputStream.use(NeshanConfig::readBounded)
            val next = NeshanConfig.verify(text, publicKey)
            next.acceptAfter(old)
            val changed = old?.digest != next.digest
            if (changed) NeshanConfig.save(cache, text)
            // Validators belong to this verified payload, never to an unverified response.
            NeshanConfig.save(metadataFile, JSONObject().put("digest", next.digest)
                .put("etag", connection.getHeaderField("ETag").orEmpty().take(256))
                .put("modified", connection.getHeaderField("Last-Modified").orEmpty().take(128)).toString())
            return Result(next, changed)
        } finally { connection.disconnect() }
    }
}
