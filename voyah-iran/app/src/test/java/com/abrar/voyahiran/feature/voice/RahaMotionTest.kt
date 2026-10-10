package com.abrar.voyahiran.feature.voice
import com.abrar.raha.feedback.RahaMotion
import org.junit.Assert.*
import org.junit.Test
class RahaMotionTest {
 @Test fun heartbeatHasTwoPeaksAndLongQuietGap() {
  assertEquals(1f,RahaMotion.heartbeat(.13f),.001f)
  assertEquals(.46f,RahaMotion.heartbeat(.24f),.001f)
  assertEquals(1f,RahaMotion.heartbeat(.35f),.001f)
  for(t in 56..209) assertEquals(0f,RahaMotion.heartbeat(t/100f),.001f)
 }
 @Test fun barsHaveIndependentMotionAndTaperedAmplitude() {
  val max=FloatArray(7)
  var opposite=false
  for(n in 1..2000) {
   val t=n/100f
   for(i in 0..6) max[i]=kotlin.math.max(max[i],RahaMotion.barHeight(i,t,1f))
   val a=RahaMotion.barHeight(2,t,1f)-RahaMotion.barHeight(2,t-.01f,1f)
   val b=RahaMotion.barHeight(3,t,1f)-RahaMotion.barHeight(3,t-.01f,1f)
   if(a*b<0)opposite=true
  }
  assertTrue(opposite)
  assertTrue(max[0]<max[1] && max[1]<max[2] && max[2]<max[3])
  assertTrue(max[3]>max[4] && max[4]>max[5] && max[5]>max[6])
 }
 @Test fun loadingRemainsContinuousAndWithinGlyphBounds() {
  for(n in 54..10000) {
   val a=RahaMotion.lineStart(n/1000f);val b=RahaMotion.lineEnd(n/1000f)
   assertTrue(a in -43.001f..43.001f && b in -43.001f..43.001f && a<=b+.001f)
   if(n>540)assertTrue(kotlin.math.abs(a-RahaMotion.lineStart((n-1)/1000f))<1f)
  }
 }
}
