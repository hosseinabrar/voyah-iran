package com.abrar.voyahiran.feature.subscription
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyPairGenerator
import java.util.Base64
class SubscriptionTokenTest {
 private fun resource(name:String)=javaClass.getResourceAsStream("/subscription/$name")!!.bufferedReader().use{it.readText()}
 @Test fun acceptsCSharpSignedToken() {
  val value=SubscriptionToken(resource("public.pem")).verify(resource("token.txt"),"L123456789012345A")
  assertEquals(30*86400L,value.expiresAt-value.issuedAt)
 }
 @Test fun rejectsDifferentVehicle() { assertThrows(IllegalArgumentException::class.java) { SubscriptionToken(resource("public.pem")).verify(resource("token.txt"),"L223456789012345A") } }
 @Test fun rejectsTamperedPayload() {
  val token=resource("token.txt").split('.').toMutableList();val decoder=Base64.getUrlDecoder()
  token[1]=Base64.getUrlEncoder().withoutPadding().encodeToString(String(decoder.decode(token[1])).replace("L123456789012345A","L223456789012345A").toByteArray())
  assertThrows(IllegalArgumentException::class.java) {SubscriptionToken(resource("public.pem")).verify(token.joinToString("."),"L223456789012345A")}
 }
 @Test fun rejectsUnsignedAlgorithm() {
  val token=resource("token.txt").split('.').toMutableList()
  token[0]=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"x\"}".toByteArray())
  assertThrows(IllegalArgumentException::class.java) {SubscriptionToken(resource("public.pem")).verify(token.joinToString("."),"L123456789012345A")}
 }
 @Test fun rejectsWrongPublicKey() {
  val key=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair().public
  val pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.encoded)+"\n-----END PUBLIC KEY-----"
  assertThrows(IllegalArgumentException::class.java) {SubscriptionToken(pem).verify(resource("token.txt"),"L123456789012345A")}
 }
 @Test fun clockUsesElapsedTimeWhenWallClockChanges() {
  assertEquals(1100,SubscriptionClock.now(1000,100000,1000,1,1000,999999999,101000,1))
  assertEquals(1100,SubscriptionClock.now(1000,100000,1000,1,1000,0,101000,1))
 }
 @Test fun rebootCannotReduceSavedTimeFloor() {
  assertEquals(1200,SubscriptionClock.now(1000,100000,1000,1,1200,0,100,2))
  assertEquals(1300,SubscriptionClock.now(1000,100000,1000,1,1200,400000,100,2))
 }
 @Test fun expiredBlockedAndMissingGrantsCannotEnableStartupFeatures() {
  val grant=SubscriptionGrant("L123456789012345A",100,200)
  assertFalse(SubscriptionAccessPolicy.allowed(null,150,false))
  assertFalse(SubscriptionAccessPolicy.allowed(grant,150,true))
  assertFalse(SubscriptionAccessPolicy.allowed(grant,99,false))
  assertTrue(SubscriptionAccessPolicy.allowed(grant,100,false))
  assertTrue(SubscriptionAccessPolicy.allowed(grant,199,false))
  assertFalse(SubscriptionAccessPolicy.allowed(grant,200,false))
  assertFalse(SubscriptionAccessPolicy.allowed(grant,201,false))
 }
 @Test fun rootStartupLeaseStartsClosedAndCannotSurviveExpiryOrReboot() {
  var now=1000L
  val lease=com.abrar.voyahiran.core.bridge.SubscriptionRuntimeLease{now}
  assertFalse(lease.active);lease.update(2);assertTrue(lease.active)
  now=3000;assertFalse(lease.active)
  lease.update(90);lease.update(0);assertFalse(lease.active)
  lease.update(90);assertFalse(com.abrar.voyahiran.core.bridge.SubscriptionRuntimeLease{now}.active)
 }
}
