package com.abrar.voyahiran.feature.subscription

import java.security.KeyFactory
import java.security.Signature
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

data class SubscriptionGrant(val vin: String, val issuedAt: Long, val expiresAt: Long)
class SubscriptionToken(publicPem: String) {
    private val decoder = Base64.getUrlDecoder()
    private val der = Base64.getMimeDecoder().decode(publicPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""))
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    private val keyId = MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }.take(16)
    fun verify(token: String, vin: String): SubscriptionGrant {
        require(token.length in 100..8192)
        val parts = token.split('.')
        require(parts.size == 3 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val header = JSONObject(String(decoder.decode(parts[0]), Charsets.UTF_8))
        require(header.getString("alg") == "RS256" && header.getString("typ") == "JWT" && header.getString("kid") == keyId)
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)); verify(decoder.decode(parts[2]))
        }
        require(valid) { "Subscription signature mismatch" }
        val data = JSONObject(String(decoder.decode(parts[1]), Charsets.UTF_8))
        require(data.getInt("v") == 1 && data.getString("iss") == "voyahiran.ir" && data.getString("aud") == "com.abrar.voyahiran")
        require(vin.matches(Regex("[A-HJ-NPR-Z0-9]{17}")) && data.getString("sub") == vin) { "Subscription VIN mismatch" }
        val issued = data.getLong("iat"); val expires = data.getLong("exp")
        require(issued > 0 && expires > issued && expires - issued <= 400L * 86400)
        return SubscriptionGrant(vin, issued, expires)
    }
}

/** Monotonic time within a boot; a persisted floor and wall-clock delta across boots.
 * A fully rooted device with its clock and storage rolled back cannot be proven fresh offline. */
object SubscriptionClock {
    fun now(issued: Long, savedWall: Long, savedElapsed: Long, savedBoot: Int, floor: Long,
            wall: Long, elapsed: Long, boot: Int): Long {
        val delta = if(savedBoot >= 0 && savedBoot == boot && elapsed >= savedElapsed) (elapsed - savedElapsed) / 1000
            else ((wall - savedWall) / 1000).coerceAtLeast(0)
        return maxOf(issued + delta, floor)
    }
}

object SubscriptionAccessPolicy {
    fun allowed(grant: SubscriptionGrant?, now: Long, blocked: Boolean): Boolean =
        !blocked && grant != null && now >= grant.issuedAt && now < grant.expiresAt
}
