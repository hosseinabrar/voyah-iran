package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30], application=Application::class, qualifiers="fa-rIR-w1775dp-h660dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherDashboardTest {
    @Test fun persianLeapDayAndNewYearUseIcu() {
        val old=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
            val leap=java.time.Instant.parse("2025-03-20T12:00:00Z").toEpochMilli()
            val newYear=java.time.Instant.parse("2025-03-21T12:00:00Z").toEpochMilli()
            assertTrue(LauncherClock.persianDate(leap),LauncherClock.persianDate(leap).contains("۳۰ اسفند ۱۴۰۳"))
            assertTrue(LauncherClock.persianDate(newYear),LauncherClock.persianDate(newYear).contains("۱ فروردین ۱۴۰۴"))
        } finally {TimeZone.setDefault(old)}
    }
    @Test fun actualDashboardRendersEmptyAndPlayingStates() {
        val view=LauncherDashboard(RuntimeEnvironment.getApplication())
        view.measure(View.MeasureSpec.makeMeasureSpec(1775,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY))
        view.layout(0,0,1775,660)
        for ((name,state) in listOf("empty" to LauncherMediaState(),"playing" to LauncherMediaState("Apple Music","Tamasha","Shadmehr Aghili",playing=true,duration=228000,position=84000))) {
            view.media=state
            val bitmap=Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output=File("build/reports/launcher/$name.png");output.parentFile.mkdirs()
            output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertTrue(output.length()>10000)
        }
    }
    @Test fun musicCardSeparatesControlsSeekAndOpen() {
        val view=LauncherDashboard(RuntimeEnvironment.getApplication())
        view.layout(0,0,1775,660)
        view.media=LauncherMediaState(duration=100000)
        var opened=0; var control=-1; var seek=-1L
        view.onOpenMusic={opened++};view.onMedia={control=it};view.onSeek={seek=it}
        fun tap(x:Float,y:Float) {
            val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_UP,x,y,0)
            view.onTouchEvent(event);event.recycle()
        }
        tap(900f,640f);assertEquals(1,opened)
        tap(1101f,576f);assertEquals(1,control);assertEquals(1,opened)
        tap(1101f,480f);assertEquals(-1L,seek);assertEquals(1,opened)
        val session=android.media.session.MediaSession(RuntimeEnvironment.getApplication(),"seek-test")
        try {
            org.robolectric.Shadows.shadowOf(session.controller).setPlaybackState(android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PAUSED,0,0f)
                .setActions(android.media.session.PlaybackState.ACTION_SEEK_TO).build())
            view.media=view.media.copy(controller=session.controller)
            tap(1101f,480f);assertEquals(50000L,seek);assertEquals(1,opened)
        } finally {session.release()}
    }
    @Test fun oemSourcesOpenTheirOwnPageWithoutAutoplay() {
        for(source in listOf("LOCAL_RADIO","USB_MUSIC","BT_MUSIC")) {
            val intent=LauncherSourceLaunch.sourceIntent(LauncherMediaState(oemSource=source))!!
            assertEquals(if(source=="LOCAL_RADIO") "com.qinggan.app.radio" else "com.qinggan.app.music",intent.component!!.packageName)
            val extras=intent.getBundleExtra("ASGARD_EXT_DATA")!!
            assertEquals(source,extras.getString("MEDIA_TYPE"))
            assertFalse(extras.getBoolean("AUTO_PLAY"))
        }
    }
    @Test fun weatherCodesAndMissingDataAreHandled() {
        val codes=listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99)
        val resources=RuntimeEnvironment.getApplication().resources
        for(code in codes) {
            assertNotEquals("Unknown",LauncherWeatherCode.description(code))
            assertNotNull(android.graphics.BitmapFactory.decodeResource(resources,LauncherWeatherCode.icon(code)))
        }
        assertEquals("Unknown",LauncherWeatherCode.description(999))
        assertEquals(com.abrar.voyahiran.R.drawable.launcher_weather_unknown,LauncherWeatherCode.icon(999))
        val data=org.json.JSONObject("""{"saved":1000000,"city":"Gorgan","current":{"temperature_2m":16.9,"weather_code":80,"is_day":1},"daily":{"temperature_2m_min":[16.5],"temperature_2m_max":[19.4]}}""")
        assertEquals("",LauncherWeather.parse(data,1000000,false).notice)
        assertTrue(LauncherWeather.parse(data,3000000,false).notice.startsWith("Saved"))
        assertTrue(LauncherWeather.parse(data,1000000,true).notice.startsWith("Saved"))
        val view=LauncherDashboard(RuntimeEnvironment.getApplication())
        view.layout(0,0,1775,660)
        for((name,state) in listOf("weather-day" to LauncherWeather.parse(data,1000000,false),"weather-night" to LauncherWeatherState("Tehran",20.0,14.0,26.0,0,false,""),"weather-night-cloudy" to LauncherWeatherState("Tehran",20.0,14.0,26.0,2,false,""))) {
            view.weather=state
            val bitmap=Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output=File("build/reports/launcher/$name.png");output.parentFile!!.mkdirs()
            output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun weatherStartupAndFailureKeepOnlyUnexpiredCache() {
        val data=org.json.JSONObject("""{"saved":1000000,"city":"Gorgan","current":{"temperature_2m":16.9,"weather_code":80,"is_day":1},"daily":{"temperature_2m_min":[16.5],"temperature_2m_max":[19.4]}}""")
        val saved=1000000L
        val startup=LauncherWeather.displayState(data,saved+1000,true)
        assertEquals("Gorgan",startup.city)
        assertEquals(16.9,startup.temperature!!,0.001)
        assertTrue(startup.notice.startsWith("Saved"))
        assertNotNull(LauncherWeather.displayState(data,saved+86400000-1,true).temperature)
        assertNull(LauncherWeather.displayState(data,saved+86400000,true).temperature)
        assertNull(LauncherWeather.displayState(data,saved-1,true).temperature)
        assertEquals("Unknown",LauncherWeather.displayState(null,saved,true).notice)
        assertNull(LauncherWeather.displayState(org.json.JSONObject("""{"saved":1000000}"""),saved,true).temperature)
        assertEquals(10000L,LauncherWeather.retryInterval(true))
        assertEquals(300000L,LauncherWeather.retryInterval(false))
    }
    @Test fun pausedMusicIsKeptOnlyInMemoryUntilSourceChanges() {
        val memory=LauncherMediaMemory()
        assertEquals("no music",memory.paused(null,false).title)
        memory.remember(LauncherMediaState("Apple Music","Tamasha","Shadmehr",playing=true,position=84000,duration=228000,sourcePackage="com.apple.android.music"))
        val paused=memory.paused(null,false)
        assertEquals("Tamasha",paused.title);assertFalse(paused.playing)
        assertEquals(84000L,paused.position);assertEquals("com.apple.android.music",paused.sourcePackage)
        assertEquals("Tamasha",memory.paused(null,false).title)
        assertEquals("no music",LauncherMediaMemory().paused(null,false).title)
        assertEquals("no music",memory.paused(null,true).title)
        memory.remember(LauncherMediaState("USB","New track",oemSource="USB_MUSIC",playing=true))
        assertEquals("USB",memory.paused(null,false).source)
    }
    @Test fun neshanCityUsesStructuredCityAndRejectsErrors() {
        assertEquals("Gorgan",LauncherCity.cityName(org.json.JSONObject("""{"status":"OK","city":"گرگان","state":"گلستان"}""")))
        assertEquals("Tehran",LauncherCity.cityName(org.json.JSONObject("""{"status":"OK","city":"تهران"}""")))
        assertNull(LauncherCity.cityName(org.json.JSONObject("""{"status":"ERROR","code":484}""")))
        assertNull(LauncherCity.cityName(org.json.JSONObject("""{"status":"OK","city":null}""")))
    }
    @Test fun cityLookupFallsBackInOrderAndNeverDisplaysCoordinates() {
        var calls=0
        assertEquals("Tehran",LauncherCity.resolveUsing({"Tehran"},{calls++;"Gorgan"}))
        assertEquals(0,calls)
        assertEquals("Gorgan",LauncherCity.resolveUsing({null},{calls++;"Gorgan"}))
        assertEquals(1,calls)
        assertEquals("Gorgan",LauncherCity.resolveUsing({error("No backend")},{"Gorgan"}))
        assertEquals("Unknown",LauncherCity.resolveUsing({" "},{null}))
        assertEquals("Unknown",LauncherCity.resolveUsing({null},{error("Offline")}))
        assertNull(LauncherCity.cityName(org.json.JSONObject("""{"status":"OK","state":"گلستان"}""")))
    }
    @Test fun bridgeNeverAcceptsPassengerOrShellInjectionForMap() {
        val policy=com.abrar.voyahiran.core.bridge.BridgePolicy
        assertNull(policy.resolve(listOf("launcher-map","org.rajman.neshan","1")))
        assertNull(policy.resolve(listOf("launcher-map","org.map;reboot","2")))
        assertNull(policy.resolve(listOf("launcher-map","com.abrar.voyahiran","2")))
        assertNull(policy.resolve(listOf("launcher-map","org.map","-1")))
        assertNotNull(policy.resolve(listOf("launcher-map","org.map","2")))
        assertNotNull(policy.resolve(listOf("launcher-map","org.map","0")))
    }
    @Test fun mapOwnershipStatusRequiresVisibleMatchingDisplay() {
        assertTrue(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=2 MAP_VISIBLE=1",2))
        assertFalse(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=20 MAP_VISIBLE=1",2))
        assertFalse(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=1 MAP_VISIBLE=1",2))
        assertFalse(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=2 MAP_VISIBLE=0",2))
        assertFalse(LauncherMapPreview.isVisibleHere("Error",2))
        val policy=com.abrar.voyahiran.core.bridge.BridgePolicy
        for(command in listOf("launcher-map-claim","launcher-map-status")) {
            assertNotNull(policy.resolve(listOf(command,"org.rajman.neshan","2")))
            assertNull(policy.resolve(listOf(command,"org.rajman.neshan","1")))
            assertNull(policy.resolve(listOf(command,"org.map;reboot","2")))
        }
    }
    @Test fun fallbackMapWholeCardShowsMap() {
        val view=LauncherDashboard(RuntimeEnvironment.getApplication())
        view.layout(0,0,1775,660)
        var claims=0;var opens=0
        view.onShowMap={claims++};view.onMap={opens++}
        fun tap(x:Float,y:Float) {
            view.onTouchEvent(android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,x,y,0))
            view.onTouchEvent(android.view.MotionEvent.obtain(0,1,android.view.MotionEvent.ACTION_UP,x,y,0))
        }
        view.mapMessage="نقشه روی نمایشگر شاگرد است"
        tap(300f,300f);assertEquals(1,claims);assertEquals(0,opens)
        tap(435f,500f);assertEquals(2,claims);assertEquals(0,opens)
        val bitmap=Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output=File("build/reports/launcher/map-fallback.png");output.parentFile!!.mkdirs()
        output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        view.mapMessage=""
        tap(300f,300f);assertEquals(2,claims);assertEquals(1,opens)
    }
}
