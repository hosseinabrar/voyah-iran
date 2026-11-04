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
/** Offline authoring only. Generated icon artwork is converted to small immutable themed PNGs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockStackArtworkBakeTest {
 private fun save(b:Bitmap,name:String){File("src/main/assets/passenger/baked/$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 @Test fun bake(){
  assumeTrue(System.getenv("BAKE_CLOCK_STACK")=="1")
  val source=BitmapFactory.decodeFile("../../design/clock-stack/icons-source.png")!!
  // A pure white-on-black imagegen atlas is used as an alpha mask; no shape is reconstructed.
  fun icon(index:Int,color:Int):Bitmap {
   val source=if(index==0)BitmapFactory.decodeFile("../../design/clock-stack/battery-source.png")!! else source
   val half=if(index==0)source.width else source.width/2;val left=(index%2)*half;val top=(index/2)*half
   var l=half;var t=half;var r=0;var b=0
   for(y in 0 until half)for(x in 0 until half)if(Color.red(source.getPixel(left+x,top+y))>32){l=minOf(l,x);r=maxOf(r,x);t=minOf(t,y);b=maxOf(b,y)}
   val out=Bitmap.createBitmap(r-l+3,b-t+3,Bitmap.Config.ARGB_8888)
   for(y in t..b)for(x in l..r){
    val alpha=Color.red(source.getPixel(left+x,top+y))
    out.setPixel(x-l+1,y-t+1,Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)))
   }
   return out
  }
  val themeNames=listOf("dark","light")
  for(theme in themeNames){
   val light=theme=="light";val ink=if(light)0xff252f3c.toInt() else Color.WHITE
   val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
   val symbols=(0..3).map{icon(it,ink)}
   for(page in 1..3){
    val b=Bitmap.createBitmap(428,426,Bitmap.Config.ARGB_8888);val c=Canvas(b)
    fun symbol(i:Int,rect:RectF){val s=symbols[i];val ratio=minOf(rect.width()/s.width,rect.height()/s.height);val w=s.width*ratio;val h=s.height*ratio;c.drawBitmap(s,null,RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),p)}
    if(page==1){
     // Match actual digit ink bounds, rather than nominal text size or padded icon bounds.
     val digits=Paint().apply{typeface=Typeface.create("sans-serif",Typeface.BOLD);textSize=66f}
     val bounds=Rect();digits.getTextBounds("11",0,2,bounds)
     fun alignedIcon(index:Int,baseline:Float){
      val s=symbols[index]
      val h=bounds.height().toFloat()
      // Optical width correction for the narrow battery at this small presentation size.
      val w=if(index==0)h*0.64f else h*s.width/s.height
      c.drawBitmap(s,Rect(1,1,s.width-1,s.height-1),RectF(58f-w/2,baseline+bounds.top,58f+w/2,baseline+bounds.bottom),p)
     }
     alignedIcon(0,145f);alignedIcon(1,305f)
     p.color=if(light)0xffcdd9e8.toInt()else 0xff596579.toInt()
     for(y in listOf(205f,365f))c.drawRoundRect(RectF(30f,y,376f,y+10f),5f,5f,p)
    }
    if(page==2)symbol(2,RectF(165f,68f,243f,123f))
    if(page==3)symbol(3,RectF(131f,168f,153f,190f))
    p.color=if(light)0xffcdd9e8.toInt()else 0xff596579.toInt();p.strokeWidth=1f
    if(page>=2)c.drawLine(30f,if(page==2)248f else 210f,378f,if(page==2)248f else 210f,p)
    if(page==3){
     c.drawLine(207f,263f,207f,328f,p)
     p.color=if(light)0xffdce6f2.toInt()else 0xff354055.toInt();c.drawRoundRect(RectF(28f,352f,378f,402f),12f,12f,p)
    }
    save(b,"clock-stack-$page-$theme")
   }
   for(page in 0..3){
    val b=Bitmap.createBitmap(12,70,Bitmap.Config.ARGB_8888);val c=Canvas(b)
    for(i in 0..3){p.color=if(i==page)0xff2688ff.toInt()else if(light)0xffc0cddd.toInt()else 0xff65748e.toInt();c.drawCircle(6f,6f+19*i,3.5f,p)}
    save(b,"clock-stack-dots-$page-$theme")
   }
   symbols.forEach{it.recycle()}
  }
  for((name,color) in listOf("blue" to 0xff2688ff.toInt(),"orange" to 0xffff961f.toInt(),"red" to 0xffed4545.toInt())){
   val b=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color}
   Canvas(b).drawCircle(5f,5f,5f,p);save(b,"clock-stack-bar-$name-cap")
   val body=Bitmap.createBitmap(1,10,Bitmap.Config.ARGB_8888);body.eraseColor(color);save(body,"clock-stack-bar-$name-body")
   File("src/main/assets/passenger/baked/clock-stack-bar-$name.png").delete()
  }
  val b=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
  Canvas(b).drawCircle(64f,64f,63f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xff2688ff.toInt()})
  save(b,"clock-stack-hold-fill");source.recycle()
 }
}
