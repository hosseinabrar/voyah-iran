package com.abrar.voyahiran.feature.navigation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class NeshanConfigTest {
    @Test fun surveySelectorsAreValidated() {
        val doc=JSONObject(File("../../voyah-iran-subscription-server/configs/neshan.source.json").readText())
        val published=NeshanConfig.verify(File("../../voyah-iran-subscription-server/deployment/configs/neshan.json").readText())
        assertEquals(doc.getLong("revision"),published.revision)
        assertTrue(published.profiles.all { it.json.getJSONObject("surveySuppression").getBoolean("enabled") })
        assertEquals(3,NeshanConfig.verify(sign(doc),publicKey).profiles.size)
        val survey=doc.getJSONArray("profiles").getJSONObject(0).getJSONObject("surveySuppression")
        survey.getJSONArray("requiredViewIds").put(0,"bad;command")
        rejects { NeshanConfig.verify(sign(doc),publicKey) }
        survey.getJSONArray("requiredViewIds").put(0,"firstTitleTextView")
        survey.put("resourcePackage","other.package")
        rejects { NeshanConfig.verify(sign(doc),publicKey) }
    }
    @Test fun cameraSelectorsAreValidatedForEveryProfile() {
        val doc=JSONObject(File("../../voyah-iran-subscription-server/configs/neshan.source.json").readText())
        assertEquals(3,NeshanConfig.verify(sign(doc),publicKey).profiles.size)
        for(i in 0 until doc.getJSONArray("profiles").length()) {
            val camera=doc.getJSONArray("profiles").getJSONObject(i).getJSONObject("cardRecenter")
            for(field in camera.keys().asSequence().toList()) {
                val old=camera.getString(field)
                camera.put(field,"bad;command")
                rejects {NeshanConfig.verify(sign(doc),publicKey)}
                camera.put(field,old)
            }
        }
    }
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(pair.public.encoded)
    private fun source(revision: Int = 1) = JSONObject().put("purpose", "voyah-iran-neshan-config")
        .put("schemaVersion", 1).put("revision", revision).put("packageName", NeshanConfig.PACKAGE)
        .put("profiles", org.json.JSONArray())
    private fun sign(doc: JSONObject): String {
        val body = doc.toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(body); sign() }
        return JSONObject().put("payload", Base64.getEncoder().encodeToString(body))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
    }
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun signatureSchemaAndRollback() {
        val signed = sign(source())
        val first = NeshanConfig.verify(signed, publicKey)
        assertEquals(1L, first.revision)
        first.acceptAfter(first)
        val second = NeshanConfig.verify(sign(source(2)), publicKey)
        second.acceptAfter(first)
        rejects { first.acceptAfter(second) }
        val changed = NeshanConfig.verify(sign(source().put("note", "changed")), publicKey)
        rejects { changed.acceptAfter(first) }
        val tampered = JSONObject(signed).put("payload", Base64.getEncoder().encodeToString(source(9).toString().toByteArray())).toString()
        rejects { NeshanConfig.verify(tampered, publicKey) }
        rejects { NeshanConfig.verify(sign(source().put("schemaVersion", 2)), publicKey) }
        rejects { NeshanConfig.verify(sign(source().put("purpose", "subscription")), publicKey) }
        rejects { NeshanConfig.readBounded(ByteArray(NeshanConfig.MAX_BYTES + 1).inputStream()) }
    }
    @Test fun conditionalGetKeepsOfflineCacheAndRejectsBadUpdates() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var body = sign(source())
        var status = 200
        var conditional: String? = null
        server.createContext("/config") { x ->
            conditional = x.requestHeaders.getFirst("If-None-Match")
            x.responseHeaders.set("ETag", "\"v1\"")
            val bytes = body.toByteArray(Charsets.UTF_8)
            x.sendResponseHeaders(status, if(status == 304) -1 else bytes.size.toLong())
            if(status != 304) x.responseBody.write(bytes)
            x.close()
        }
        server.start()
        val directory = Files.createTempDirectory("neshan-config-test").toFile()
        val cache = File(directory, "config.json")
        val client = NeshanConfigDownload(cache, "http://127.0.0.1:${server.address.port}/config", publicKey)
        try {
            assertTrue(client.update().changed); assertNull(conditional)
            val saved = cache.readText(); val stamp = cache.lastModified()
            status = 304
            assertFalse(client.update().changed); assertEquals("\"v1\"", conditional)
            assertEquals(stamp, cache.lastModified())
            status = 200; body = sign(source(2))
            assertTrue(client.update().changed)
            val good = cache.readText()
            body = saved
            rejects { client.update() }
            assertEquals(good, cache.readText())
            body = JSONObject(good).put("payload", Base64.getEncoder().encodeToString("bad".toByteArray())).toString()
            rejects { client.update() }
            assertEquals(good, cache.readText())
            status = 503
            try { client.update(); fail("503 accepted") } catch (_: IllegalStateException) {}
            assertEquals(good, cache.readText())
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    @Test fun publicationAndAdSelectorsAreValidated() {
        val artifact=File("../../voyah-iran-subscription-server/deployment/configs/neshan.json")
        val catalog=NeshanConfig.verify(artifact.readText())
        // Publication advances independently of this selector-validation test.
        assertTrue("Expected publication without route restart",catalog.revision >= 8L)
        for (profile in catalog.profiles) assertFalse(profile.json.has("routeRestart"))
        for (profile in catalog.profiles) {
            assertTrue(profile.json.getJSONObject("adSuppression").getBoolean("enabled"))
            assertEquals(3,profile.json.getJSONObject("androidTheme").getInt("value"))
        }
        val p=catalog.profiles.single {it.versionCode==70799L}
        assertTrue(p.json.getJSONObject("adSuppression").getBoolean("enabled"))
        assertEquals("Setting3",p.json.getJSONObject("androidTheme").getString("preferences"))
        val doc=source().put("profiles",org.json.JSONArray().put(JSONObject(p.json.toString())))
        val entry=doc.getJSONArray("profiles").getJSONObject(0)
        entry.getJSONObject("adSuppression").put("viewIds",org.json.JSONArray().put(-1))
        rejects {NeshanConfig.verify(sign(doc),publicKey)}
        entry.remove("adSuppression")
        entry.getJSONObject("androidTheme").put("preferences","../escape")
        rejects {NeshanConfig.verify(sign(doc),publicKey)}
        entry.remove("androidTheme")
        assertEquals(1,NeshanConfig.verify(sign(doc),publicKey).profiles.size)
    }
    @Test fun publishedCatalogSignatureAndTypedProfile() {
        // Optional standalone invocation supplies the actual publication artifact.
        val path = System.getProperty("neshan.published") ?: return
        val catalog = NeshanConfig.verify(File(path).readText())
        assertEquals(3, catalog.profiles.size)
        val p = catalog.profiles.single { it.versionCode == 70789L }
        assertEquals(70789L, p.versionCode)
        assertEquals("l10.b", p.json.getJSONObject("hook").getString("stepClass"))
        assertTrue("ورود به حالت رانندگی" in p.labels("entryLabels"))
        assertEquals("a90.b", catalog.profiles.single { it.versionCode == 70794L }.json.getJSONObject("hook").getString("stepClass"))
        assertEquals("d90.b", catalog.profiles.single { it.versionCode == 70799L }.json.getJSONObject("hook").getString("stepClass"))
        val profile = JSONObject(p.json.toString())
        val second = JSONObject(p.json.toString()).put("id", "test-second-version")
            .put("versionCode", 123456).put("apkSha256", "0".repeat(64))
        val multiple = source().put("profiles", org.json.JSONArray().put(profile).put(second))
        assertEquals(2, NeshanConfig.verify(sign(multiple), publicKey).profiles.size)
        second.put("apkSha256", p.apkSha256)
        rejects { NeshanConfig.verify(sign(multiple), publicKey) }
        second.put("apkSha256", "0".repeat(64))
        second.getJSONObject("hook").put("updateMethod", "b();evil()")
        rejects { NeshanConfig.verify(sign(multiple), publicKey) }
    }
}
