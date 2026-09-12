package com.abrar.voyahiran.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.abrar.voyahiran.R
import com.abrar.voyahiran.databinding.ActivityMainBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Inflates and measures the actual Android XML; no browser mockup or root commands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "fa-rIR-w1280dp-h720dp-land-mdpi")
class SettingsLayoutTest {
    @Test fun persianLocaleKeepsRailOnPhysicalRightAndContentRtl() {
        val binding = layout(1280, 720)
        assertEquals(binding.root.width, binding.navigationRail.right)
        assertTrue(binding.navigationRail.width > 0)
        assertEquals(View.LAYOUT_DIRECTION_RTL, binding.settingsPage.root.layoutDirection)
        assertTrue(binding.settingsPage.root.width > binding.navigationRail.width)
        assertLabelHasNoEllipsis(binding.settingsPage.timeZoneCorrection)
        assertLabelHasNoEllipsis(binding.settingsPage.starBack)
    }

    @Test
    @Config(qualifiers = "fa-rIR-w800dp-h480dp-land-mdpi")
    fun smallerHeadUnitKeepsControlAccessibleByScrolling() {
        val binding = layout(800, 480)
        assertTrue(binding.settingsPage.root.width > 0)
        assertLabelHasNoEllipsis(binding.settingsPage.timeZoneCorrection)
        assertTrue(binding.settingsPage.timeZoneCorrection.height >= 56)
    }

    @Test
    @Config(qualifiers = "fa-rIR-w480dp-h800dp-port-mdpi")
    fun narrowWindowWrapsTheLabelInsteadOfTruncatingIt() {
        val binding = layout(480, 800)
        assertLabelHasNoEllipsis(binding.settingsPage.timeZoneCorrection)
        assertTrue(binding.settingsPage.timeZoneCorrection.layout.lineCount > 1)
    }

    private fun layout(width: Int, height: Int): ActivityMainBinding {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_VoyahIran)
        return ActivityMainBinding.inflate(LayoutInflater.from(context)).also {
            it.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            it.root.layout(0, 0, width, height)
        }
    }

    private fun assertLabelHasNoEllipsis(view: TextView) {
        assertTrue(view.width > 0)
        assertNotNull(view.layout)
        for (line in 0 until view.layout.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
        assertTrue(view.height >= view.layout.height + view.compoundPaddingTop + view.compoundPaddingBottom)
    }
}
