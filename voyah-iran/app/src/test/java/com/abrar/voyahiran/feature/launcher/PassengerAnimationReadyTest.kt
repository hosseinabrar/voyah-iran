package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class PassengerAnimationReadyTest {
 private fun await(view:Any,field:String){
  val f=view.javaClass.getDeclaredField(field).apply{isAccessible=true}
  val deadline=System.nanoTime()+30_000_000_000L
  while(!f.getBoolean(view)&&System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle()}
  assertTrue("$field must publish complete set",f.getBoolean(view))
 }
 @Test fun coldStartLoadsEveryAirAndFragranceFrameBeforeReady(){
  val c=RuntimeEnvironment.getApplication()
  val ac=PassengerClimateView(c);val fragrance=PassengerFragranceView(c)
  await(ac,"airReady");await(fragrance,"artReady")
  val images=ac.javaClass.getDeclaredField("images").apply{isAccessible=true}.get(ac) as Map<*,*>
  for(theme in listOf("dark","light")){
   for(side in listOf("left","center","right"))for(i in 0..74)assertNotNull(images["passenger/baked/air-$theme-face-$side-$i.png"])
   for(i in 0..29)assertNotNull(images["passenger/baked/air-$theme-window-$i.png"])
  }
  val f=fragrance.javaClass.getDeclaredField("images").apply{isAccessible=true}.get(fragrance) as Map<*,*>
  for(i in 0..27)assertNotNull(f["fragrance_anim_${(i*2).toString().padStart(2,'0')}"])
  val second=PassengerClimateView(c);await(second,"airReady")
  val secondImages=second.javaClass.getDeclaredField("images").apply{isAccessible=true}.get(second) as Map<*,*>
  assertSame(images.values.first(),secondImages[images.keys.first()])
 }
}
