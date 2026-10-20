package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Opt-in offline artwork placement preview. Does not modify packaged artwork. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatCutoutPreviewTest {
 @Test fun renderBothSeats() {
  assumeTrue(System.getenv("SEAT_CUTOUT_PREVIEW")=="1")
  preparePassengerArtwork("baked")
  val source=BitmapFactory.decodeFile(System.getenv("SEAT_CUTOUT_SOURCE"))!!
  val output=File("build/reports/seat-cutout-preview").apply{mkdirs()}
  val context=RuntimeEnvironment.getApplication()
  val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  for(heat in listOf(false,true)) {
   val rows=if(!heat)listOf(floatArrayOf(610f,25f,886f,25f),floatArrayOf(490f,541f,913f,556f),floatArrayOf(357f,601f,833f,651f),floatArrayOf(364f,715f,816f,740f))
    else listOf(floatArrayOf(1370f,25f,1650f,25f),floatArrayOf(1264f,541f,1682f,556f),floatArrayOf(1134f,601f,1605f,651f),floatArrayOf(1145f,715f,1596f,740f))
   val target=listOf(floatArrayOf(414f,168f,447f,170f),floatArrayOf(389f,270f,450f,273f),floatArrayOf(336f,284f,417f,288f),floatArrayOf(338f,316f,418f,317f))
   val overlays=(0..1).map { side ->
    val bitmap=Bitmap.createBitmap(950,356,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap)
    for(i in 0..2) {
     fun quad(a:FloatArray,b:FloatArray)=floatArrayOf(a[0],a[1],a[2],a[3],b[2],b[3],b[0],b[1])
     val src=quad(rows[i],rows[i+1]);val dst=quad(target[i],target[i+1])
     if(side==1)for(j in 0..3)dst[j*2]=1040f-dst[j*2]
     val path=Path().apply{moveTo(dst[0],dst[1]);for(j in 1..3)lineTo(dst[j*2],dst[j*2+1]);close()}
     val matrix=Matrix().apply{setPolyToPoly(src,0,dst,0,4)}
     canvas.save();canvas.clipPath(path);canvas.drawBitmap(source,matrix,paint);canvas.restore()
    }
    bitmap
   }
   for(light in listOf(false,true)) {
    val view=PassengerSeatsView(context);view.layout(0,0,556,228);view.light=light
    val state=JSONObject().put("fresh",true)
    for(side in listOf("LEFT","RIGHT"))for(feature in listOf("VENTILATION","HEATING","MASS")) {
     val on=feature==if(heat)"HEATING"else "VENTILATION"
     state.put("FRONT_SEAT_${feature}_SWITCH_$side",if(on)2 else 1)
     state.put("FRONT_SEAT_${feature}_${if(feature=="MASS")"INTEN"else "COMMAND"}_$side",if(on)2 else 0)
    }
    view.update(state);kotlinx.coroutines.runBlocking{view.prepareArtwork()}
    @Suppress("UNCHECKED_CAST")
    val artwork=PassengerSeatsView::class.java.getDeclaredField("artwork").apply{isAccessible=true}.get(view) as MutableMap<String,Bitmap>
    for((i,side) in listOf("left","right").withIndex())artwork["seat_effect_${if(heat)"hot"else "vent"}_$side"]=overlays[i]
    val result=Bitmap.createBitmap(1112,456,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(result);canvas.scale(2f,2f);view.draw(canvas)
    File(output,"${if(light)"light"else "dark"}-${if(heat)"heat"else "cool"}.png").outputStream().use{result.compress(Bitmap.CompressFormat.PNG,100,it)}
   }
  }
 }
}
