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
import org.robolectric.annotation.*
import java.io.File
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1775dp-h660dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LauncherWeatherStackTest {
    @Before fun clear(){RuntimeEnvironment.getApplication().getSharedPreferences("launcher",0).edit().clear().commit()}
    private val start=ZonedDateTime.parse("2026-11-05T15:00:00+03:30[Asia/Tehran]").toInstant().toEpochMilli()
    private fun fixture()=LauncherWeatherState("Tehran",26.0,15.0,28.0,2,true,"",
        (0..7).map{LauncherWeatherHour(start+it*3_600_000L,listOf(26.0,25.0,24.0,22.0)[it%4],if(it==2)3 else 2,true,if(it==2)20 else 10)},
        (0..2).map{LauncherWeatherDay(start-15*3_600_000L+it*86_400_000L,listOf(15.0,16.0,14.0)[it],listOf(28.0,29.0,24.0)[it],listOf(2,0,61)[it],listOf(10,0,65)[it])},"Asia/Tehran")
    @Test fun rendersAllPagesAndUnavailableForecastBothThemes(){
        preparePassengerArtwork("baked")
        val dashboard=LauncherDashboard(RuntimeEnvironment.getApplication());dashboard.layout(0,0,1775,660)
        dashboard.weatherView.now={start+900_000L};dashboard.weather=fixture()
        preloadWeatherIcons(dashboard.weatherView)
        for(light in listOf(false,true))for(page in 0..2){
            dashboard.lightTheme=light;dashboard.weatherView.show(page,false)
            val b=Bitmap.createBitmap(428,218,Bitmap.Config.ARGB_8888)
            Canvas(b).also{it.translate(-1331f,-442f);dashboard.draw(it)}
            val f=File("build/reports/weather-stack/${if(light)"light"else "dark"}-$page.png");f.parentFile.mkdirs()
            f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};assertTrue(f.length()>2000)
        }
        dashboard.weather=LauncherWeatherState()
        for(page in 0..2){dashboard.weatherView.show(page,false);dashboard.draw(Canvas(Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888)))}
    }
    @Test fun selectionPersistsAndHorizontalGestureDoesNotSwitchWeather(){
        val app=RuntimeEnvironment.getApplication();val v=LauncherWeatherStack(app);v.layout(0,0,428,218)
        v.show(1,false);assertEquals(1,LauncherWeatherStack(app).page)
        fun event(action:Int,x:Float,y:Float){MotionEvent.obtain(0,0,action,x,y,0).also{v.onTouchEvent(it);it.recycle()}}
        event(MotionEvent.ACTION_DOWN,100f,100f);event(MotionEvent.ACTION_MOVE,220f,102f);event(MotionEvent.ACTION_UP,240f,104f)
        assertEquals(1,v.page)
        event(MotionEvent.ACTION_DOWN,100f,160f);event(MotionEvent.ACTION_MOVE,102f,70f);event(MotionEvent.ACTION_UP,102f,40f)
        assertEquals(2,v.page)
        v.show(8,false);assertEquals(2,v.page);v.show(-1,false);assertEquals(0,v.page)
    }
    @Test fun currentHourAndDayRemainInForecastAndRollOverInForecastTimezone(){
        val v=LauncherWeatherStack(RuntimeEnvironment.getApplication());var time=start+900_000L
        v.now={time};v.state=fixture()
        fun labels(name:String)=LauncherWeatherStack::class.java.getDeclaredField(name).apply{isAccessible=true}.get(v) as List<*>
        assertEquals("15:00",labels("hourLabels").first());assertEquals("Thu",labels("dayLabels").first())
        time+=3_600_000L;v.tick();assertEquals("16:00",labels("hourLabels").first())
        time=start+9*3_600_000L;v.tick();assertEquals("Fri",labels("dayLabels").first())
    }
}

internal fun preloadWeatherIcons(view:LauncherWeatherStack) {
    val loader=LauncherWeatherStack::class.java.getDeclaredField("bitmaps").apply{isAccessible=true}.get(view)
    @Suppress("UNCHECKED_CAST")
    val images=LauncherBitmapLoader::class.java.getDeclaredField("images").apply{isAccessible=true}.get(loader) as android.util.LruCache<Int,Bitmap>
    for(code in listOf(-1,0,2,3,61)) {
        val id=LauncherWeatherCode.icon(code,true)
        images.put(id,LauncherWeatherArtwork.decode(view.resources,id))
    }
}
