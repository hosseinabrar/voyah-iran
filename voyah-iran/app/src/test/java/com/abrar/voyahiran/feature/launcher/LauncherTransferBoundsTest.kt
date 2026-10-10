package com.abrar.voyahiran.feature.launcher
import android.app.Application
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherTransferBoundsTest {
 @Test fun followsAcknowledgedFrameworkPolicyInPhysicalPixels() {
  val nonce="1:"+"a".repeat(32)
  assertEquals(Rect(145,50,1920,720),LauncherTransferBounds.resolve(1920,720,nonce,nonce,true))
  assertEquals(Rect(145,50,1600,720),LauncherTransferBounds.resolve(1600,720,null,"1:default",true))
  for((request,ack) in listOf("0" to "0",nonce to "0",null to null,"1:invalid" to "1:invalid"))
   assertEquals(Rect(0,0,1920,720),LauncherTransferBounds.resolve(1920,720,request,ack,true))
  assertEquals(Rect(0,0,1920,720),LauncherTransferBounds.resolve(1920,720,nonce,nonce,false))
  assertEquals(Rect(0,0,320,240),LauncherTransferBounds.resolve(320,240,nonce,nonce,true))
 }
}
