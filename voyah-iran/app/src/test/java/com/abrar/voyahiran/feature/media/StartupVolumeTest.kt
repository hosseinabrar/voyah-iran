package com.abrar.voyahiran.feature.media

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import com.abrar.voyahiran.R
import com.abrar.voyahiran.core.bridge.BridgePolicy
import com.abrar.voyahiran.databinding.PageDisplayMediaMapBinding
import com.abrar.voyahiran.feature.navigation.NavigationPreferences
import com.abrar.voyahiran.feature.subscription.VipCheckBox
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class,qualifiers="fa-rIR-w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupVolumeTest {
    @Test fun onlyAccOnOncePerStartupAndOffRearms() {
        assertFalse(StartupVolumeAutomation.shouldApply(null,5,4,false))
        assertFalse(StartupVolumeAutomation.shouldApply(0,5,4,false))
        assertFalse(StartupVolumeAutomation.shouldApply(1,5,4,false))
        assertTrue(StartupVolumeAutomation.shouldApply(2,5,4,false))
        assertFalse(StartupVolumeAutomation.shouldApply(2,5,5,false))
        assertTrue(StartupVolumeAutomation.shouldApply(2,5,5,true))
        assertFalse(StartupVolumeAutomation.shouldApply(2,-1,5,true))
    }
    @Test fun disabledByDefaultAndRangeEnforced() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=NavigationPreferences(context)
        assertFalse(prefs.startupVolumeEnabled);assertEquals(5,prefs.startupVolume)
        for(value in listOf(1,20)) {
            prefs.startupVolume=value;assertEquals(value,NavigationPreferences(context).startupVolume)
            assertNotNull(BridgePolicy.resolve(listOf("music-startup-volume",value.toString())))
        }
        for(value in listOf(0,21,30,-1)) {
            assertNull(BridgePolicy.resolve(listOf("music-startup-volume",value.toString())))
            try {prefs.startupVolume=value;fail("out of range")}catch(_:IllegalArgumentException){}
        }
        context.getSharedPreferences("display_media_map",Context.MODE_PRIVATE).edit().putInt("startupVolume",99).commit()
        assertEquals(20,prefs.startupVolume)
    }
    @Test fun renderOrdinaryOption() {
        val context=RuntimeEnvironment.getApplication();context.setTheme(R.style.Theme_VoyahIran)
        val binding=PageDisplayMediaMapBinding.inflate(LayoutInflater.from(context))
        assertFalse(binding.startupVolumeEnabled is VipCheckBox)
        assertEquals(19,binding.startupVolumeSlider.max)
        assertFalse(binding.startupVolumeEnabled.isChecked)
        // Apply the same enabled state as renderMusic; native SeekBar initialization
        // can reset the XML enabled attribute on some Android versions.
        binding.startupVolumeSlider.isEnabled=binding.startupVolumeEnabled.isChecked
        binding.startupVolumeControls.alpha=0.45f
        assertFalse(binding.startupVolumeSlider.isEnabled)
        assertEquals(View.VISIBLE,binding.startupVolumeControls.visibility)
        assertEquals("۵",binding.startupVolumeValue.text.toString())
        binding.startupVolumeControls.visibility=View.VISIBLE
        binding.musicAutoPlay.visibility=View.VISIBLE
        binding.musicPauseOnDoor.visibility=View.VISIBLE
        val group=binding.startupVolumeEnabled.parent as android.view.ViewGroup
        for(option in listOf(binding.musicAutoPlay,binding.musicPauseOnDoor,binding.mediaControls)) {
            assertTrue(group.indexOfChild(option)<group.indexOfChild(binding.startupVolumeEnabled))
        }
        assertSame(binding.startupVolumeControls,group.getChildAt(group.childCount-1))
        val card=binding.startupVolumeEnabled.parent as View
        card.layoutDirection=View.LAYOUT_DIRECTION_RTL
        card.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        card.layout(0,0,900,card.measuredHeight)
        binding.startupVolumeEnabled.jumpDrawablesToCurrentState()
        assertTrue(binding.startupVolumeValue.top>=binding.startupVolumeSlider.height/2)
        val image=Bitmap.createBitmap(900,card.height,Bitmap.Config.ARGB_8888)
        Canvas(image).apply {drawColor(context.getColor(R.color.background));card.draw(this)}
        val file=File("build/reports/startup-volume/music-settings.png");file.parentFile!!.mkdirs()
        file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        binding.startupVolumeEnabled.isChecked=true
        binding.startupVolumeSlider.isEnabled=true
        binding.startupVolumeControls.alpha=1f
        binding.startupVolumeSlider.progress=9
        binding.startupVolumeValue.text="۱۰"
        card.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        card.layout(0,0,900,card.measuredHeight)
        binding.startupVolumeEnabled.jumpDrawablesToCurrentState()
        binding.startupVolumeSlider.jumpDrawablesToCurrentState()
        val ten=Bitmap.createBitmap(900,card.height,Bitmap.Config.ARGB_8888)
        Canvas(ten).apply {drawColor(context.getColor(R.color.background));card.draw(this)}
        File(file.parentFile,"music-settings-10.png").outputStream().use{ten.compress(Bitmap.CompressFormat.PNG,100,it)}
        ten.recycle()
    }
}
