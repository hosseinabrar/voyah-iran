package com.abrar.voyahiran.feature.voice
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.abrar.raha.feedback.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RahaOrbRenderTest {
 @Test fun rendersAllStatesInBothThemes() {
  val image=Bitmap.createBitmap(1280,512,Bitmap.Config.ARGB_8888)
  val c=Canvas(image)
  for((row,light) in listOf(false,true).withIndex()) {
   val bg=if(light)0xffeaf2fd.toInt() else 0xff15233a.toInt()
   c.save();c.clipRect(0,row*256,1280,(row+1)*256);c.drawColor(bg);c.restore()
   for((i,state) in listOf(FeedbackState.WAITING,FeedbackState.LISTENING,FeedbackState.PROCESSING,FeedbackState.SUCCESS,FeedbackState.FAILURE).withIndex()) {
    val v=RahaOrbView(RuntimeEnvironment.getApplication()).apply { this.light=light;phase=state;level=1f;layout(0,0,256,256) }
    ShadowSystemClock.advanceBy(Duration.ofMillis(800))
    c.save();c.translate(i*256f,row*256f);v.draw(c);c.restore()
    assertNotEquals(bg,image.getPixel(i*256+128,row*256+128))
   }
  }
  val f=File("build/reports/raha/raha-native-states.png");f.parentFile?.mkdirs()
  f.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
 }
}
