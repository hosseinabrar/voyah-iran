package com.abrar.voyahiran.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import com.abrar.voyahiran.R
import com.abrar.voyahiran.databinding.PageComfortBinding
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
@Config(sdk = [30], application = Application::class, qualifiers = "fa-rIR-w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComfortMemoryRenderTest {
    @Test fun allMemoriesAreVipAndCameraFollowsThem() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_VoyahIran)
        val binding = PageComfortBinding.inflate(LayoutInflater.from(context))
        assertEquals(VipCheckBox::class.java, binding.aebMemory.javaClass)
        assertEquals(VipCheckBox::class.java, binding.pedestrianMemory.javaClass)
        assertEquals(VipCheckBox::class.java, binding.shadeDeparture.javaClass)
        assertEquals(VipCheckBox::class.java, binding.shadeReady.javaClass)
        assertEquals(VipCheckBox::class.java, binding.driveMemory.javaClass)
        assertEquals(VipCheckBox::class.java, binding.recoveryMemory.javaClass)
        assertEquals(VipCheckBox::class.java, binding.energyModeMemory.javaClass)
        assertEquals(VipCheckBox::class.java, binding.dowAmbient.javaClass)
        binding.avmTurnCard.visibility = View.VISIBLE
        binding.root.visibility = View.VISIBLE
        binding.root.setBackgroundColor(context.getColor(R.color.background))
        binding.aebMemory.isEnabled = true
        binding.aebMemory.isChecked = true
        binding.dowAmbient.isEnabled = true
        binding.dowAmbient.isChecked = true
        binding.dowMode.visibility = View.VISIBLE
        assertTrue(binding.dowDoorOnly.isChecked)
        assertFalse(binding.dowAlways.isChecked)
        val width = 1120
        val height = 1710
        binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        binding.root.layout(0, 0, width, height)
        binding.aebMemory.jumpDrawablesToCurrentState()
        assertTrue((binding.energyModeMemory.parent as View).top > (binding.recoveryMemory.parent as View).top)
        assertTrue((binding.energyModeMemory.parent as View).top < (binding.aebMemory.parent as View).top)
        for (line in 0 until binding.energyModeMemory.layout.lineCount)
            assertEquals(0, binding.energyModeMemory.layout.getEllipsisCount(line))
        assertTrue((binding.aebMemory.parent as View).top > (binding.recoveryMemory.parent as View).top)
        assertTrue((binding.pedestrianMemory.parent as View).top > (binding.aebMemory.parent as View).top)
        assertTrue(binding.avmTurnCard.top > (binding.pedestrianMemory.parent as View).top)
        assertTrue(binding.shadeDepartureCard.top > binding.avmTurnCard.top)
        assertTrue(binding.shadeReadyCard.top > binding.shadeDepartureCard.top)
        assertTrue(binding.dowAmbientCard.top > binding.shadeReadyCard.top)
        assertTrue(binding.aebMemory.width > 0)
        for (line in 0 until binding.aebMemory.layout.lineCount)
            assertEquals(0, binding.aebMemory.layout.getEllipsisCount(line))
        for (line in 0 until binding.pedestrianMemory.layout.lineCount)
            assertEquals(0, binding.pedestrianMemory.layout.getEllipsisCount(line))
        for (line in 0 until binding.shadeDeparture.layout.lineCount)
            assertEquals(0, binding.shadeDeparture.layout.getEllipsisCount(line))
        for (line in 0 until binding.shadeReady.layout.lineCount)
            assertEquals(0, binding.shadeReady.layout.getEllipsisCount(line))
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        binding.root.draw(Canvas(image))
        val output = File(System.getProperty("voyah.renderDir", "build/reports/comfort-memory"), "comfort-memory.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val card = binding.dowAmbientCard
        // Render the entitled/checked appearance only; the plain test Application has no VIP account.
        binding.dowAmbient.buttonDrawable?.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        binding.dowAmbient.buttonDrawable?.jumpToCurrentState()
        val preview = Bitmap.createBitmap(card.width + 80, card.height + 60, Bitmap.Config.ARGB_8888)
        Canvas(preview).apply {
            drawColor(context.getColor(R.color.background))
            translate(40f, 30f); card.draw(this)
        }
        File(output.parentFile, "dow-comfort.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
