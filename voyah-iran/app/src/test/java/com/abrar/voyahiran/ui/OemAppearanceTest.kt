package com.abrar.voyahiran.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.abrar.voyahiran.R
import com.abrar.voyahiran.databinding.ActivityMainBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders actual production layouts, without starting services or issuing vehicle commands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "fa-rIR-w1775dp-h670dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OemAppearanceTest {
    @Test @Config(qualifiers = "fa-rIR-w1775dp-h670dp-land-night-mdpi")
    fun darkPagesRenderWithoutClippedControls() = render("dark")

    @Test @Config(qualifiers = "fa-rIR-w1775dp-h670dp-land-notnight-mdpi")
    fun lightPagesRenderWithoutClippedControls() = render("light")

    private fun render(theme: String) {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_VoyahIran)
        val b = ActivityMainBinding.inflate(LayoutInflater.from(context))
        val pages = listOf("settings" to b.settingsPage.root, "display" to b.displayMediaMapPage.root,
            "comfort" to b.comfortPage.root, "apollo" to b.apolloPage.root,
            "voice" to b.voicePage.root, "permissions" to b.permissionsPage.root)
        val tabs = listOf(b.settingsTab, b.displayMediaMapTab, b.comfortTab, b.apolloTab, b.voiceTab, b.permissionsTab)
        b.aboutPage.root.visibility = View.GONE
        b.diagnosticsPage.root.visibility = View.GONE
        b.settingsPage.timeZoneCorrection.apply { isEnabled = true; isChecked = true }
        b.apolloPage.subscription.isChecked = true
        b.apolloPage.speedWarningExact.isChecked = true
        b.displayMediaMapPage.appThemeChoices.check(R.id.appThemeVehicle)
        b.voicePage.wakeVolume.progress = 40
        pages.forEachIndexed { index, (name, page) ->
            pages.forEach { it.second.visibility = if (it.second === page) View.VISIBLE else View.GONE }
            tabs.forEachIndexed { i, tab -> tab.isSelected = i == index }
            b.root.measure(View.MeasureSpec.makeMeasureSpec(1775, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(670, View.MeasureSpec.EXACTLY))
            b.root.layout(0, 0, 1775, 670)
            assertEquals(1775, b.navigationRail.right)
            val bitmap = Bitmap.createBitmap(1775, 670, Bitmap.Config.ARGB_8888)
            b.root.draw(Canvas(bitmap))
            val output = File("build/reports/oem-ui/$theme-$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            inspect(page)
            if (page is android.widget.ScrollView) {
                page.scrollTo(0, page.getChildAt(0).height)
                val bottom = Bitmap.createBitmap(1775, 670, Bitmap.Config.ARGB_8888)
                b.root.draw(Canvas(bottom))
                File("build/reports/oem-ui/$theme-$name-bottom.png").outputStream().use { bottom.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bottom.recycle()
                page.scrollTo(0, 0)
            }
        }
    }

    private fun inspect(view: View) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view.text.isNotEmpty() && view !is android.widget.EditText) {
            assertTrue("No width: ${view.text}", view.width > 0)
            view.layout?.let { layout ->
                assertTrue("Clipped vertically: ${view.text}", view.height >= layout.height + view.compoundPaddingTop + view.compoundPaddingBottom)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i))
    }
}
