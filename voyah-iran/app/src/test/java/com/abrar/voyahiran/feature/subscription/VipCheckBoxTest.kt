package com.abrar.voyahiran.feature.subscription

import android.app.Activity
import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[28])
class VipCheckBoxTest {
    private class Box(context: Activity) : VipCheckBox(context) {
        var vip = false
        override val allowed get() = vip
    }
    @Test fun entitlementMasksSavedSelectionAndRestoresWithoutClicking() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val box=Box(activity)
        var writes=0
        box.setOnClickListener { writes++ }
        box.isChecked=true
        assertFalse(box.isChecked)
        box.vip=true;box.refreshEntitlement()
        assertTrue(box.isChecked)
        box.vip=false;box.refreshEntitlement()
        assertFalse(box.isChecked)
        box.isEnabled=false
        assertTrue(box.isEnabled)
        box.performClick()
        assertEquals(0,writes)
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        box.vip=true;box.refreshEntitlement()
        assertTrue(box.isChecked)
        assertFalse(box.isEnabled)
        assertEquals(0,writes)
    }
}
