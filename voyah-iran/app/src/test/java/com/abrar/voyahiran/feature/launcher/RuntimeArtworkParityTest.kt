package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.*
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.abrar.raha.feedback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuntimeArtworkParityTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val out=File("build/reports/runtime-artwork").apply{mkdirs()}
    private fun save(b:Bitmap,name:String){File(out,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun render(v:View,size:Int,bg:Int):Bitmap {
        v.layout(0,0,size,size)
        return Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also{it.eraseColor(bg);v.draw(Canvas(it))}
    }
    private fun compare(a:Bitmap,b:Bitmap,label:String){
        var total=0L;var changed=0
        for(y in 0 until a.height)for(x in 0 until a.width){
            val p=a.getPixel(x,y);val q=b.getPixel(x,y)
            val error=maxOf(abs(Color.red(p)-Color.red(q)),abs(Color.green(p)-Color.green(q)),abs(Color.blue(p)-Color.blue(q)))
            total+=error;if(error>24)changed++
        }
        val mean=total.toDouble()/(a.width*a.height);val fraction=changed.toDouble()/(a.width*a.height)
        File(out,"metrics.txt").appendText("$label meanMaxChannelError=$mean fractionOver24=$fraction\n")
        assertTrue("$label mean=$mean",mean<1.8)
        assertTrue("$label changed=$fraction",fraction<.02)
    }
    @Test fun orbMatchesOriginalAtEveryStateInBothThemes(){
        RahaArtwork.warm(app)
        val deadline=System.nanoTime()+10_000_000_000L
        while(!RahaArtwork.ready&&System.nanoTime()<deadline){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}
        assertTrue(RahaArtwork.ready)
        val states=listOf(FeedbackState.WAITING,FeedbackState.LISTENING,FeedbackState.PROCESSING,FeedbackState.SUCCESS,FeedbackState.FAILURE,FeedbackState.UNRECOGNIZED)
        val before=Bitmap.createBitmap(1536,512,Bitmap.Config.ARGB_8888);val after=Bitmap.createBitmap(1536,512,Bitmap.Config.ARGB_8888)
        for((row,light) in listOf(false,true).withIndex())for((column,state)in states.withIndex())for(size in listOf(256,440))for(age in listOf(100L,800L)){
            val old=ReferenceRahaOrbView(app).apply{this.light=light;phase=state;level=.65f}
            val new=RahaOrbView(app).apply{this.light=light;phase=state;level=.65f}
            val started=SystemClock.uptimeMillis()-age
            old.heartbeatStartedAt=started;new.heartbeatStartedAt=started
            for(v in listOf<View>(old,new)){
                v.javaClass.getDeclaredField("started").apply{isAccessible=true}.setLong(v,started)
                v.javaClass.getDeclaredField("transitionStarted").apply{isAccessible=true}.setLong(v,started)
            }
            val bg=if(light)0xffeaf2fd.toInt()else 0xff15233a.toInt()
            val a=render(old,size,bg);val b=render(new,size,bg)
            compare(a,b,"raha-$state-$light-$size-$age")
            if(size==256&&age==800L){Canvas(before).drawBitmap(a,column*256f,row*256f,null);Canvas(after).drawBitmap(b,column*256f,row*256f,null)}
            a.recycle();b.recycle()
        }
        save(before,"raha-before");save(after,"raha-after")
        val pair=Bitmap.createBitmap(1536,1024,Bitmap.Config.ARGB_8888);Canvas(pair).apply{drawBitmap(before,0f,0f,null);drawBitmap(after,0f,512f,null)};save(pair,"raha-before-after")
    }
    @Test fun loadingMatchesOriginal(){
        preparePassengerArtwork("baked")
        val sheet=Bitmap.createBitmap(480,240,Bitmap.Config.ARGB_8888);val c=Canvas(sheet)
        for((row,light)in listOf(false,true).withIndex()){
            val old=ReferenceLauncherLoadingView(app).apply{this.light=light}
            val new=LauncherLoadingView(app).apply{this.light=light}
            // Original loading view uses 1775px as its reference width.
            val a=render(old,1775,if(light)0xffeaf2fd.toInt()else 0xff15233a.toInt())
            val b=render(new,1775,if(light)0xffeaf2fd.toInt()else 0xff15233a.toInt())
            val cropA=Bitmap.createBitmap(a,827,827,120,120);val cropB=Bitmap.createBitmap(b,827,827,120,120)
            compare(cropA,cropB,"loading-$light")
            c.drawBitmap(cropA,0f,row*120f,null);c.drawBitmap(cropB,240f,row*120f,null)
            a.recycle();b.recycle();cropA.recycle();cropB.recycle()
        };save(sheet,"loading-before-after")
    }
    @Test fun weatherMatchesOriginalPixelForPixel(){
        org.junit.Assume.assumeTrue(System.getenv("ARTWORK_BACKUP")!=null)
        val backup=File(System.getenv("ARTWORK_BACKUP"),"weather")
        val ids=(listOf(-1)+(0..99)).flatMap{listOf(LauncherWeatherCode.icon(it,true),LauncherWeatherCode.icon(it,false))}.distinct()
        val sheet=Bitmap.createBitmap(1440,((ids.size+3)/4)*360,Bitmap.Config.ARGB_8888);val c=Canvas(sheet)
        for((i,id)in ids.withIndex()){
            val name=app.resources.getResourceEntryName(id)
            val old=ReferenceLauncherWeatherArtwork.decodeOriginal(File(backup,"$name.png"))
            val new=LauncherWeatherArtwork.decode(app.resources,id)
            assertTrue("Weather must be pixel-identical: $name",old.sameAs(new))
            val x=i%4*360f;val y=i/4*360f
            c.save();c.clipRect(x,y,x+360,y+360);c.drawColor(if(i%2==0)0xff15233a.toInt()else 0xffeaf2fd.toInt());c.restore()
            c.drawBitmap(old,null,RectF(x,y,x+180,y+180),null)
            c.drawBitmap(new,null,RectF(x+180,y,x+360,y+180),null)
            old.recycle();new.recycle()
        };save(sheet,"weather-before-after")
    }
}
