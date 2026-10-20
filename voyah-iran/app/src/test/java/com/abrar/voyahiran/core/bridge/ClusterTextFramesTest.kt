package com.abrar.voyahiran.core.bridge

import android.app.Application
import android.graphics.*
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.abrar.voyahiran.core.bridge.clustertext.ClusterTextAssets
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

/** Detached, native Android raster test of production text replacement, OEM white geometry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30], application=Application::class, qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterTextFramesTest {
 @Test fun renderDetachedFrames() {
  val context=RuntimeEnvironment.getApplication()
  val out=File("build/reports/cluster-text-frames").apply{mkdirs()}
  val report=StringBuilder()
  for(constrained in listOf(false,true)) for(long in listOf(false,true)) {
   val root=FrameLayout(context)
   val panel=ConstraintLayout(context)
   root.addView(panel,FrameLayout.LayoutParams(477,506))
   fun add(id:Int,view:TextView,text:String,size:Float,color:Int,w:Int,h:Int,x:Int,y:Int) {
    view.id=id;view.text=text;view.textSize=size;view.setTextColor(color);view.gravity=android.view.Gravity.CENTER
    val lp=ConstraintLayout.LayoutParams(w,h).apply{leftToLeft=0;topToTop=0;leftMargin=x;topMargin=y}
    panel.addView(view,lp)
   }
   add(101,ClusterTextAssets.BoldTextView(context,context),"Apple Music",36f,0xff2d3442.toInt(),200,-2,175,43)
   val song=ClusterTextAssets.MarqueeBoldTextView(context,context).apply{maxWidth=340}
   add(102,song,if(long)"A very long song title — Tamasha live performance extended edition" else "Tamasha",34f,0xff2d3442.toInt(),340,-2,105,274)
   val artist=ClusterTextAssets.MarqueeTextView(context).apply{maxWidth=340}
   add(103,artist,if(long)"Shadmehr Aghili and the live concert orchestra" else "Shadmehr Aghili",28f,0xff5c6171.toInt(),340,-2,105,320)
   add(104,TextView(context),"01:24 / 03:48",20f,0xff2d3442.toInt(),210,-2,170,387)
   ClusterTextAssets.install(root,context)
   // Match production styleMusic's title customization, after replacement.
   panel.findViewById<TextView>(101).apply{textSize=28.8f;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;isSelected=false}
   if(constrained)for(id in 101..103)panel.findViewById<TextView>(id).setHorizontallyScrolling(false)
   var previous=0L
   val captured=mutableListOf<IntArray>()
   for(ms in listOf(0L,500L,2000L,5000L)) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms-previous));previous=ms
    root.measure(View.MeasureSpec.makeMeasureSpec(477,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(506,View.MeasureSpec.EXACTLY));root.layout(0,0,477,506)
    val bitmap=Bitmap.createBitmap(477,506,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(0xffeef3fa.toInt());root.draw(canvas)
    val tag=(if(constrained)"constrained-" else "")+(if(long)"long" else "short")
    File(out,"$tag-$ms.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    for(id in 101..104){
     val t=panel.findViewById<TextView>(id)
     assertTrue("$tag $id bounded layout",t.layout.width<=t.width)
     assertEquals("$tag $id single line",1,t.lineCount)
     var ink=0
     for(y in t.top until t.bottom)for(x in t.left until t.right)if(bitmap.getPixel(x,y)!=0xffeef3fa.toInt())ink++
     assertTrue("$tag $id visible text at $ms",ink>50)
    }
    val pixels=IntArray(340*39);bitmap.getPixels(pixels,0,340,105,274,340,39);captured+=pixels
    for(id in 101..104){val t=panel.findViewById<TextView>(id);report.append("$tag t=$ms id=$id bounds=${t.left},${t.top},${t.width}x${t.height} scroll=${t.scrollX},${t.scrollY} layout=${t.layout?.width}x${t.layout?.height} lineLeft=${t.layout?.getLineLeft(0)} lines=${t.lineCount}\n")}
    bitmap.recycle()
   }
   if(long)assertFalse("Long text must move while detached",captured.first().contentEquals(captured[2]))
   else assertTrue("Short text must remain still",captured.first().contentEquals(captured[2]))
  }
  File(out,"geometry.txt").writeText(report.toString())
 }
}
