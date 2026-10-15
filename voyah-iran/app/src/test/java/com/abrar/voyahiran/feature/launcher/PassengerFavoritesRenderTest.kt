package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
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
class PassengerFavoritesRenderTest {
    @Test fun emptyDefaultsAndBlankCardTouchesExitEditing(){
        val view=PassengerFavoritesView(context);view.layout(0,0,546,662)
        view.bind(apps(),List(5){""})
        assertEquals(List(5){""},view.selections())
        var opened=0;var picked=0;var drawer=0
        view.onOpen={opened++};view.onPick={picked++};view.onAllApps={drawer++}
        fun tap(x:Float,y:Float){for(action in listOf(0,1)){
            val event=MotionEvent.obtain(0,1,action,x,y,0);view.onTouchEvent(event);event.recycle()
        }}
        view.bind(apps(),apps().map{it.id})
        for((x,y) in listOf(273f to 150f,30f to 50f,145f to 240f,220f to 151f,273f to 640f)){
            view.beginEdit();tap(x,y);assertFalse("Blank point $x,$y must dismiss edit",view.editing)
        }
        assertEquals(0,opened);assertEquals(0,picked)
        view.beginEdit();tap(399f,533f);assertFalse(view.editing);assertEquals(1,drawer)
    }
    private val context get()=RuntimeEnvironment.getApplication()
    private fun apps()=listOf("Neshan" to "neshan.webp","Apple Music" to "apple-music.jpeg","Namava" to "namava.jpeg","Video" to "video.png","Music player" to "audio.png").mapIndexed{i,p->
        val icon=javaClass.getResourceAsStream("/passenger-app-icons/${p.second}")!!.use{Drawable.createFromStream(it,p.second)}
        LauncherAppsEntry("app$i",p.first,icon)
    }
    private fun render(view:View,name:String,w:Int=546,h:Int=662){
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val out=File("build/reports/passenger-favorites").apply{mkdirs()}
        File(out,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun renderBothThemesAndEditingStates(){
        val apps=apps();val view=PassengerFavoritesView(context)
        for(light in listOf(false,true)){
            view.light=light;view.dismissEdit();view.bind(apps,apps.map{it.id});val theme=if(light)"light" else "dark"
            render(view,"favorites-$theme")
            view.beginEdit();render(view,"favorites-$theme-edit")
            view.dismissEdit();view.bind(apps,listOf("app0","","app2","app3","app4"));render(view,"favorites-$theme-add")
            render(PassengerAppPickerView(context,view.availableApps(),light,1f){},"favorites-$theme-picker",800,540)
        }
    }
    @Test fun launchDeleteSwapAddAndStaleCatalog(){
        val apps=apps();val view=PassengerFavoritesView(context);view.layout(0,0,546,662);view.bind(apps,apps.map{it.id})
        var opened="";var drawer=0;var pick=-1;var saved=emptyList<String>()
        view.onOpen={opened=it};view.onAllApps={drawer++};view.onPick={pick=it};view.onSave={saved=it}
        fun event(a:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,1,a,x,y,0);view.onTouchEvent(e);e.recycle()}
        fun tap(x:Float,y:Float){event(0,x,y);event(1,x,y)}
        tap(145f,151f);assertEquals("app0",opened)
        view.beginEdit();tap(202f,94f);assertEquals("",saved[0]);assertEquals(listOf("app0"),view.availableApps().map{it.id})
        tap(145f,151f);assertEquals(0,pick);view.select(0,"app0");assertEquals("app0",saved[0])
        event(0,145f,151f);event(2,399f,151f);event(1,399f,151f);assertEquals(listOf("app1","app0"),saved.take(2))
        view.select(2,"app0");assertEquals("app2",view.selections()[2])
        tap(399f,533f);assertEquals(1,drawer);assertFalse(view.editing)
        view.bind(apps.drop(1),saved);assertFalse(view.selections().contains("app0"))
        view.bind(emptyList(),saved);assertEquals(List(5){""},view.selections())
    }
}
