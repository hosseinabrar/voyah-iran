package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.abrar.voyahiran.core.bridge.BridgePolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="en-w1775dp-h660dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherVehicleDashboardTest {
    @org.junit.Before fun prepareBakedArtwork(){preparePassengerArtwork("baked")}
    @Test fun drivePopupCoversCurrentModeAndRendersBothThemes() {
        for(scale in listOf(1f,0.75f)) {
            val bounds=LauncherDrivePopup.bounds((1775*scale).toInt(),(660*scale).toInt())
            assertTrue(bounds.contains((1264*scale).toInt(),(516*scale).toInt()))
            assertTrue(bounds.bottom<(560*scale).toInt())
        }
        val v=view();v.animationsEnabled=false;v.state=sample().copy(drive=2)
        for(light in listOf(false,true)) {
            v.lightTheme=light
            runBlocking { v.artwork.prepare() }
            val popup=LauncherDrivePopup.create(v,v.state,light){}
            val rect=LauncherDrivePopup.bounds(v.width,v.height)
            val content=popup.contentView
            content.measure(View.MeasureSpec.makeMeasureSpec(rect.width(),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(rect.height(),View.MeasureSpec.EXACTLY))
            content.layout(0,0,rect.width(),rect.height())
            val bitmap=Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);v.draw(canvas)
            canvas.save();canvas.translate(rect.left.toFloat(),rect.top.toFloat());content.draw(canvas);canvas.restore()
            val card=Bitmap.createBitmap(bitmap,1182,0,575,660)
            val file=File("build/reports/launcher/drive-popup-${if(light)"light" else "dark"}.png")
            file.parentFile.mkdirs();file.outputStream().use{card.compress(Bitmap.CompressFormat.PNG,100,it)}
            card.recycle();bitmap.recycle()
        }
        v.artwork.close()
    }
    @Test fun compassUsesIndependentDialAndUprightLabels() {
        val v=view();v.animationsEnabled=false
        v.compass=LauncherCompassState(90f,1180.0,0.0,0.0)
        val dial=v.getChildAt(0) as LauncherCompassDial
        assertEquals(-90f,dial.rotation,0.01f)
        for(i in 1 until dial.childCount)assertEquals(90f,dial.getChildAt(i).rotation,0.01f)
        v.transitioning=true;v.compass=LauncherCompassState(120f)
        assertEquals(-90f,dial.rotation,0.01f)
        v.transitioning=false;assertEquals(-120f,dial.rotation,0.01f)
    }
    private fun sample()=LauncherVehicleState(drive=1,height=5,adjusting=0,recovery=3,energy=2,
        modes=linkedMapOf("smart" to 1,"electric" to 2,"fuel" to 3,"save" to 4),speed=0,pressure=890,
        maintain=1,suspended=0,sensorError=0,manual=1,fresh=true,supportsSuspension=true,supportsOuting=true)
    private fun view()=LauncherVehicleDashboard(RuntimeEnvironment.getApplication()).apply {
        measure(View.MeasureSpec.makeMeasureSpec(1775,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY))
        layout(0,0,1775,660);state=sample()
        runBlocking {preload(this)}
    }
    private fun draw(v:View,name:String?=null) {
        val b=Bitmap.createBitmap(1775,660,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        if(name!=null) {
            val f=File("build/reports/launcher/$name.png");f.parentFile.mkdirs()
            f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
            val card=Bitmap.createBitmap(b,0,0,575,660)
            File(f.parentFile,"$name-energy.png").outputStream().use{card.compress(Bitmap.CompressFormat.PNG,100,it)}
            card.recycle()
        }
        b.recycle()
    }
    @Test fun preloadPreparesSuspensionWithoutVisitingPageTwo() {
        val v=view()
        assertNotNull(v.artwork.body)
        assertTrue(v.artwork.wheels.isEmpty())
        val first=v.artwork.body
        runBlocking {v.artwork.prepare()}
        assertSame(first,v.artwork.body)
        v.state=sample().copy(height=1)
        runBlocking {v.artwork.prepare()}
        assertNotSame(first,v.artwork.body)
        val dark=v.artwork.body
        v.lightTheme=true
        runBlocking {v.artwork.prepare()}
        assertNotSame(dark,v.artwork.body)
        v.artwork.close()
    }
    @Test fun renderActualPageInBothThemesAndNoData() {
        val v=view();v.compass=LauncherCompassState(0f,1180.0,0.0,0.0)
        // Load real frame without scheduling the lifecycle animation loop in Robolectric.
        val field=v.artwork.javaClass.getDeclaredField("body").apply {isAccessible=true}
        for(light in listOf(false,true)) {
            v.lightTheme=light
            val path="launcher/ccc/body_${if(light)"light" else "dark"}_60.png"
            field.set(v.artwork,v.context.assets.open(path).use(android.graphics.BitmapFactory::decodeStream))
            draw(v,"page2-${if(light)"light" else "dark"}")
        }
        v.state=LauncherVehicleState();v.compass=LauncherCompassState();draw(v,"page2-unavailable")
    }
    @Test fun commandsUseRealReadbackAndDisableUnsupportedControls() {
        val v=view();var command="";v.onCommand={op,value->command="$op/$value"}
        fun tap(x:Float,y:Float){val e=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0);v.onTouchEvent(e);e.recycle()}
        draw(v);tap(490f,600f);assertEquals("recovery/4",command)
        assertEquals(3,v.state.recovery) // No fake confirmation.
        command="";v.state=v.state.copy(speed=40);draw(v);tap(1690f,600f);assertEquals("",command)
        v.state=v.state.copy(speed=39);draw(v);tap(1690f,600f);assertEquals("height/3",command)
        command="";v.state=v.state.copy(maintain=2);draw(v);tap(1250f,600f);assertEquals("",command)
        v.state=v.state.copy(fresh=false);draw(v);tap(490f,600f);assertEquals("",command)
    }
    @Test fun swipeCancelsCardTapAndReturnsHome() {
        val context=RuntimeEnvironment.getApplication()
        val pager=LauncherPager(context)
        var taps=0
        repeat(2){pager.addView(object:View(context){override fun onTouchEvent(e:MotionEvent):Boolean {if(e.action==MotionEvent.ACTION_UP)taps++;return true}},FrameLayout.LayoutParams(-1,-1))}
        pager.measure(View.MeasureSpec.makeMeasureSpec(1775,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY));pager.layout(0,0,1775,660)
        fun send(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,10,action,x,y,0);pager.dispatchTouchEvent(e);e.recycle()}
        send(MotionEvent.ACTION_DOWN,800f,300f);send(MotionEvent.ACTION_MOVE,300f,305f);send(MotionEvent.ACTION_UP,200f,305f)
        assertEquals(1,pager.page);assertEquals(0,taps)
        pager.show(0,false);assertEquals(0,pager.page)
        send(MotionEvent.ACTION_DOWN,800f,300f);send(MotionEvent.ACTION_UP,800f,300f);assertEquals(1,taps)
        assertEquals(0,LauncherVehicleState.swipePage(0,-500f,600f,1775))
    }
    @Test fun invalidStatesAndBridgeRequestsFailClosed() {
        val s=LauncherVehicleState.parse(JSONObject("""{"ASC_MODE_SELECT":-1,"IVI_SOC_MODESET":-1,"modes":{}}"""))
        assertNull(s.height);assertEquals("—",s.heightLabel);assertFalse(s.suspensionEnabled)
        assertNull(BridgePolicy.resolve(listOf("launcher-vehicle","height","4")))
        assertNull(BridgePolicy.resolve(listOf("launcher-vehicle","recovery","1")))
        assertNotNull(BridgePolicy.resolve(listOf("launcher-vehicle","energy","hybrid")))
        assertNotNull(BridgePolicy.resolve(listOf("launcher-vehicle","read")))
        assertEquals(20,sample().copy(height=9).bodyFrame)
        assertEquals(40,sample().copy(height=7).bodyFrame)
        assertEquals(60,sample().bodyFrame);assertEquals(80,sample().copy(height=1).bodyFrame)
    }
    @Test fun allOriginalAnimationFramesArePackaged() {
        val assets=RuntimeEnvironment.getApplication().assets
        for(theme in listOf("dark","light")) {
            for(i in 20..80) assets.open("launcher/ccc/body_${theme}_$i.png").use { assertTrue(it.read()>=0) }
            for(type in listOf("static","up","down"))for(corner in listOf("fr","fl","rl","rr"))for(i in 0..19)
                assets.open("launcher/ccc/suspension_${theme}_${type}_${corner}_$i.png").use { assertTrue(it.read()>=0) }
        }
    }
    @Test fun leavingPageStopsArtworkUpdates() = runBlocking {
        var updates=0
        val artwork=LauncherSuspensionArtwork(RuntimeEnvironment.getApplication()){updates++}
        try {
            artwork.state=sample();artwork.start(this)
            withTimeout(10000){while(artwork.body==null)delay(20)}
            assertTrue(updates>0)
            val idleCount=updates
            delay(250);assertEquals("Static pose must not emit animation frames",idleCount,updates)
            artwork.light=true
            withTimeout(10000){while(updates==idleCount)delay(20)}
            artwork.stop();val before=artwork.body;val count=updates
            artwork.state=sample().copy(height=1);delay(200)
            assertSame(before,artwork.body);assertEquals(count,updates)
            artwork.start(this)
            withTimeout(10000){while(artwork.body===before)delay(20)}
            assertTrue(updates>count)
        } finally {artwork.close()}
    }
}

