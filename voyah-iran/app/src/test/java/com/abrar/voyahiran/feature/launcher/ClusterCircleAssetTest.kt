package com.abrar.voyahiran.feature.launcher
import android.app.Application
import android.graphics.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
/** Offline only: same Canvas/Paint circle as LiveMapView, baked once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterCircleAssetTest {
 @Test fun bakeTestAppCircle() {
  assumeTrue(File("build/bake-cluster-circle").exists())
  val bitmap=Bitmap.createBitmap(700,700,Bitmap.Config.ARGB_8888)
  Canvas(bitmap).drawCircle(350f,350f,350f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE })
  // Relay samples red; preserve original alpha coverage in grayscale.
  val pixels=IntArray(700*700)
  bitmap.getPixels(pixels,0,700,0,0,700,700)
  for(i in pixels.indices) { val a=Color.alpha(pixels[i]);pixels[i]=Color.rgb(a,a,a) }
  bitmap.setPixels(pixels,0,700,0,0,700,700)
  File("src/main/res/drawable-nodpi/cluster_map_mask_circle.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
  bitmap.recycle()
 }
}
