package com.abrar.voyahiran.feature.launcher
import android.app.Application
import android.graphics.*
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import org.json.JSONObject
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1775dp-h660dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class LauncherClockStackTest {
 @Before fun clear(){RuntimeEnvironment.getApplication().getSharedPreferences("launcher",0).edit().clear().commit()}
 @Test fun rendersAllPagesBothThemes(){
  preparePassengerArtwork("baked")
  val d=LauncherDashboard(RuntimeEnvironment.getApplication());d.clockTime={1793796840000L};d.layout(0,0,1775,660)
  d.clockView.telemetry=LauncherTelemetry(11.0,11.0,1850.0,6.8,128.4,138.0,6.2,18.5,batteryRange=168.0,fuelRange=482.0)
  for(light in listOf(false,true))for(page in 0..3){
   d.lightTheme=light;d.clockView.show(page,false)
   val b=Bitmap.createBitmap(428,426,Bitmap.Config.ARGB_8888)
   val c=Canvas(b);c.translate(-1331f,0f);d.draw(c)
   val f=File("build/reports/clock-stack/${if(light)"light"else "dark"}-$page.png");f.parentFile.mkdirs()
   f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};assertTrue(f.length()>2000)
  }
 }
 @Test fun clockPageIsPixelIdenticalToOriginalClockRenderer(){
  preparePassengerArtwork("baked")
  val d=LauncherDashboard(RuntimeEnvironment.getApplication());d.clockTime={1793796840000L};d.layout(0,0,1775,660)
  val original=LauncherDashboard::class.java.getDeclaredMethod("drawClock",Canvas::class.java).apply{isAccessible=true}
  for(light in listOf(false,true)){
   d.lightTheme=light;d.clockView.show(0,false)
   val expected=Bitmap.createBitmap(428,426,Bitmap.Config.ARGB_8888)
   val actual=Bitmap.createBitmap(428,426,Bitmap.Config.ARGB_8888)
   val c=Canvas(expected);c.translate(-1331f,0f);original.invoke(d,c)
   d.clockView.draw(Canvas(actual))
   assertTrue("Clock changed in theme $light",Bitmap.createBitmap(expected,0,0,398,426).sameAs(Bitmap.createBitmap(actual,0,0,398,426)))
  }
 }
 @Test fun selectionSurvivesRecreationAndSwipesStayBounded(){
  val app=RuntimeEnvironment.getApplication();val v=LauncherClockStack(app){};v.layout(0,0,428,426)
  v.show(2,false);assertEquals(2,LauncherClockStack(app){}.page)
  fun event(action:Int,y:Float){v.onTouchEvent(MotionEvent.obtain(0,100,action,200f,y,0))}
  event(MotionEvent.ACTION_DOWN,300f);event(MotionEvent.ACTION_MOVE,150f);event(MotionEvent.ACTION_UP,150f)
  assertEquals(3,v.page);assertEquals(3,LauncherClockStack(app){}.page)
  v.show(20,false);assertEquals(3,v.page);v.show(-1,false);assertEquals(0,v.page)
 }
 @Test fun tapAndCancelledHoldNeverReset(){
  val v=LauncherClockStack(RuntimeEnvironment.getApplication()){};v.layout(0,0,428,426);v.show(3,false);v.telemetry=LauncherTelemetry(distance=128.4)
  var resets=0;v.onReset={resets++}
  fun event(a:Int,y:Float=375f){v.onTouchEvent(MotionEvent.obtain(0,100,a,200f,y,0))}
  event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900));assertEquals(0,resets)
  event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_MOVE,300f);event(MotionEvent.ACTION_CANCEL)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900));assertEquals(0,resets)
  v.show(3,false);event(MotionEvent.ACTION_DOWN)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(3999));assertEquals(0,resets)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1));assertEquals(1,resets)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(5000));assertEquals(1,resets)
  event(MotionEvent.ACTION_UP);assertEquals(1,resets)
 }
 @Test fun pageSettlesThroughIntermediatePositions(){
  val v=LauncherClockStack(RuntimeEnvironment.getApplication()){};v.layout(0,0,428,426);v.show(1,false)
  val field=LauncherClockStack::class.java.getDeclaredField("offset").apply{isAccessible=true}
  v.show(2,true);assertEquals(-426f,field.getFloat(v),0.1f)
  val animator=LauncherClockStack::class.java.getDeclaredField("animation").apply{isAccessible=true}.get(v) as android.animation.ValueAnimator
  assertEquals(300L,animator.duration);animator.currentPlayTime=150
  assertTrue(field.getFloat(v)>-852f && field.getFloat(v)<-426f)
  animator.end()
  assertEquals(-852f,field.getFloat(v),0.1f)
 }
 @Test fun warningThresholdsAreStrictlyBelowTenAndTwenty(){
  assertEquals("red",LauncherClockStack.levelTone(9.9))
  assertEquals("orange",LauncherClockStack.levelTone(10.0))
  assertEquals("orange",LauncherClockStack.levelTone(19.9))
  assertEquals("blue",LauncherClockStack.levelTone(20.0))
 }
 @Test fun focusLossCancelsHold(){
  val v=LauncherClockStack(RuntimeEnvironment.getApplication()){};v.layout(0,0,428,426);v.show(3,false);v.telemetry=LauncherTelemetry(distance=1.0)
  var resets=0;v.onReset={resets++}
  v.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,200f,375f,0))
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2000));v.onWindowFocusChanged(false)
  shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));assertEquals(0,resets)
 }
 @Test fun renderWarningsAndFourSecondHold(){
  preparePassengerArtwork("baked")
  val d=LauncherDashboard(RuntimeEnvironment.getApplication())
  d.measure(android.view.View.MeasureSpec.makeMeasureSpec(1775,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(660,1073741824));d.layout(0,0,1775,660)
  val v=d.clockView
  fun save(name:String){
   val b=Bitmap.createBitmap(428,426,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.translate(-1331f,0f);d.draw(c)
   val f=File("build/reports/clock-stack/$name.png");f.parentFile.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
  }
  for(light in listOf(false,true)){
   d.lightTheme=light;v.telemetry=LauncherTelemetry(18.0,8.0,1850.0,6.8,128.4,138.0,6.2,18.5,batteryRange=39.0)
   v.show(1,false);save("${if(light)"light"else "dark"}-warnings")
   v.show(3,false);v.resetting=false;var resets=0;v.onReset={resets++;v.resetting=true}
   v.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,200f,375f,0))
   shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
   assertEquals(0,resets);assertTrue(v.holdProgress in .45f.. .55f);save("${if(light)"light"else "dark"}-hold-2s")
   shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
   assertEquals(1,resets);save("${if(light)"light"else "dark"}-hold-4s")
   v.onTouchEvent(MotionEvent.obtain(0,4000,MotionEvent.ACTION_UP,200f,375f,0));v.resetting=false
  }
 }
 @Test fun fuelEstimateRequiresBothCompatibleRanges(){
  assertEquals(482.0,LauncherTelemetry.parse(JSONObject("""{"totalRange":650,"batteryRange":168}""")).fuelRange!!,0.01)
  assertNull(LauncherTelemetry.parse(JSONObject("""{"totalRange":100,"batteryRange":168}""")).fuelRange)
  assertNull(LauncherTelemetry.parse(JSONObject("""{"totalRange":650}""")).fuelRange)
 }
 @Test fun invalidTelemetryNeverBecomesARealReading(){
  val s=LauncherTelemetry.parse(JSONObject("""{"battery":101,"fuel":-9999,"rpm":-2147483648,"instantFuel":-9999,"distance":128.4,"minutes":138,"averageFuel":6.2,"averagePower":18.5}"""))
  assertNull(s.battery);assertNull(s.fuel);assertNull(s.rpm);assertNull(s.instantFuel)
  assertEquals(128.4,s.distance!!,0.001);assertEquals(138.0,s.minutes!!,0.001)
  assertEquals(LauncherTelemetry(),LauncherTelemetry.parse(JSONObject()))
  assertEquals(-8.0,LauncherTelemetry.parse(JSONObject("{\"averagePower\":-8}")).averagePower!!,0.01)
 }
}
