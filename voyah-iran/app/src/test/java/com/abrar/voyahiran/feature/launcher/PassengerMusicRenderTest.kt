package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1920dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PassengerMusicRenderTest {
    private fun sample()=PassengerMusicPresentation("Tamasha","Shadmehr Aghili",
        javaClass.getResourceAsStream("/passenger-music/tamasha.jpg")!!.use{BitmapFactory.decodeStream(it)},
        javaClass.getResourceAsStream("/passenger-app-icons/apple-music.jpeg")!!.use{Drawable.createFromStream(it,"Apple Music")},true,72000,180000,true)
    @Test fun renderThemesMuteAndMissingMedia(){
        val view=PassengerMusicView(RuntimeEnvironment.getApplication());view.layout(0,0,556,256)
        val output=File("build/reports/passenger-music").apply{mkdirs()}
        for(light in listOf(false,true)){
            view.light=light;val theme=if(light)"light"else "dark"
            for((name,s,n) in listOf(Triple("playing",sample(),11),Triple("muted",sample().copy(playing=false),0),Triple("empty",PassengerMusicPresentation(),30),Triple("long-title",sample().copy(title="A very long music title that must not cover controls",artist="A very long artist name that needs truncation"),11))){
                view.update(s);view.updateVolume(n)
                val bitmap=Bitmap.createBitmap(556,256,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
                File(output,"music-$theme-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                assertEquals(255,android.graphics.Color.alpha(bitmap.getPixel(10,100)));bitmap.recycle()
            }
        }
    }
    @Test fun transportVolumeSeekingAndCancellation(){
        val view=PassengerMusicView(RuntimeEnvironment.getApplication());view.layout(0,0,556,256)
        view.update(sample());val commands=mutableListOf<Pair<String,String>>();view.onCommand={a,b->commands.add(a to b)}
        fun event(a:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,1,a,x,y,0);view.onTouchEvent(e);e.recycle()}
        fun tap(x:Float,y:Float){event(0,x,y);event(1,x,y)}
        tap(412f,74f);assertEquals("media" to "previous",commands.last())
        tap(466f,74f);assertEquals("media" to "play",commands.last())
        tap(520f,74f);assertEquals("media" to "next",commands.last())
        tap(80f,80f);assertEquals("source" to "",commands.last())
        tap(50f,200f);assertEquals("mute" to "",commands.last())
        tap(273f,200f);assertEquals("volume" to "15",commands.last())
        event(0,270f,200f);event(2,700f,200f);event(1,700f,200f);assertEquals("volume" to "30",commands.last())
        event(0,270f,200f);event(2,0f,200f);event(1,0f,200f);assertEquals("volume" to "0",commands.last())
        tap(353.5f,126f);assertEquals("seek" to "90000",commands.last())
        view.update(sample().copy(seek=false));val count=commands.size;tap(353.5f,126f);assertEquals(count,commands.size)
        event(0,270f,200f);event(3,270f,200f);assertEquals(count,commands.size)
    }
}
