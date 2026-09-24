package com.abrar.voyahiran.feature.navigation

import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Data-only schema. Never accepts JavaScript, shell commands, URLs or filesystem paths. */
internal object NeshanConfig {
    const val MAX_BYTES = 524288
    const val FILE_NAME = "neshan-config.json"
    const val PACKAGE = "org.rajman.neshan.traffic.tehran.navigator"
    data class Profile(val id: String, val versionCode: Long, val versionName: String,
                       val apkSha256: String, val json: JSONObject) {
        val driving: JSONObject get() = json.getJSONObject("driving")
        fun labels(name: String): List<String> = driving.getJSONArray(name).let { a ->
            List(a.length()) { a.getString(it) }
        }
    }
    data class Catalog(val revision: Long, val digest: String, val profiles: List<Profile>) {
        fun acceptAfter(old: Catalog?) {
            require(old == null || revision > old.revision ||
                (revision == old.revision && digest == old.digest)) { "Config rollback or reused revision" }
        }
    }
    fun verify(envelope: String, publicPem: String = NeshanConfigTrust.publicPem): Catalog {
        require(envelope.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES)
        val data = JSONObject(envelope)
        val body = Base64.getDecoder().decode(data.getString("payload"))
        val signature = Base64.getDecoder().decode(data.getString("signature"))
        val der = Base64.getMimeDecoder().decode(publicPem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", ""))
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(body); verify(signature)
        }) { "Neshan config signature invalid" }
        val doc = JSONObject(String(body, Charsets.UTF_8))
        require(doc.getString("purpose") == "voyah-iran-neshan-config" && doc.getInt("schemaVersion") == 1)
        require(doc.getString("packageName") == PACKAGE)
        val revision = doc.getLong("revision"); require(revision > 0)
        val entries = doc.getJSONArray("profiles"); require(entries.length() in 0..256)
        val profiles = List(entries.length()) { i ->
            val p = entries.getJSONObject(i)
            val id = p.getString("id"); require(id.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
            val version = p.getLong("versionCode"); require(version > 0)
            val name = p.getString("versionName"); require(name.length in 1..80)
            val sha = p.getString("apkSha256"); require(sha.matches(Regex("[a-f0-9]{64}")))
            val h = p.getJSONObject("hook")
            for (n in listOf("stepClass", "updateClass", "resetClass"))
                require(h.getString(n).matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]{0,199}")))
            for (n in listOf("updateMethod", "resetMethod", "distance", "type", "modifier", "road", "roadExtra", "turnIndex"))
                require(h.getString(n).matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,99}")))
            if (h.has("routeState")) {
                val state = h.getJSONObject("routeState")
                for (n in listOf("ownerClass", "stateClass"))
                    require(state.getString(n).matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]{0,199}")))
                for (n in listOf("instanceField", "activeMethod", "engineMethod"))
                    require(state.getString(n).matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,99}")))
            }
            val d = p.getJSONObject("driving")
            for (n in listOf("activeIds", "routeIds", "moreScopeIds", "entryLabels")) {
                val a = d.getJSONArray(n); require(a.length() in 1..16)
                for (j in 0 until a.length()) {
                    val text = a.getString(j)
                    require(text.isNotBlank() && text.length <= 160 && text.none { it.isISOControl() })
                    if (n.endsWith("Ids")) require(text.matches(Regex("[A-Za-z0-9_]+")))
                }
            }
            require(d.getString("moreLabel").let { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) })
            Profile(id, version, name, sha, p)
        }
        require(profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.map { it.apkSha256 }.distinct().size == profiles.size)
        return Catalog(revision, hash(body), profiles)
    }
    fun read(file: File): String {
        file.inputStream().use { input ->
            return readBounded(input)
        }
    }
    fun readBounded(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            require(out.size() + n <= MAX_BYTES) { "Config too large" }
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }
    fun save(file: File, text: String) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temp = File(file.path + ".new")
        temp.outputStream().use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(temp.renameTo(file)) { "Cannot replace config cache" }
    }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    fun fileHash(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
