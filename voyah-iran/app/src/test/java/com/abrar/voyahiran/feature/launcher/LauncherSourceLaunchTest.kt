package com.abrar.voyahiran.feature.launcher

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30], application=Application::class)
class LauncherSourceLaunchTest {
    @Test fun factoryMusicAndRadioPagesMatchOemBundles() {
        listOf("USB_MUSIC" to "PAGE_HOME","KW_MUSIC" to "PAGE_HOME",
            "LOCAL_RADIO" to "radio_page_fm","XMLA_MUSIC" to "radio_page_xmly",
            "RADIO_YUNTING" to "radio_page_yunting").forEach { (source,page) ->
            val intent=requireNotNull(LauncherSourceLaunch.sourceIntent(LauncherMediaState(oemSource=source)))
            val extras=requireNotNull(intent.getBundleExtra("ASGARD_EXT_DATA"))
            assertEquals(source,extras.getString("MEDIA_TYPE"))
            assertEquals(page,extras.getString("PAGE_NAME"))
            assertEquals(if(source in setOf("USB_MUSIC","KW_MUSIC"))"com.qinggan.app.music" else "com.qinggan.app.radio",intent.component?.packageName)
            assertFalse(extras.getBoolean("AUTO_PLAY"))
        }
        assertEquals("radio_page_am",LauncherSourceLaunch.sourceIntent(LauncherMediaState(oemSource="LOCAL_RADIO",artist="AM"))?.getBundleExtra("ASGARD_EXT_DATA")?.getString("PAGE_NAME"))
        assertEquals("com.pateo.rdsapp",LauncherSourceLaunch.sourcePackage("RDS"))
        assertEquals("com.qinggan.app.dab",LauncherSourceLaunch.sourcePackage("DAB"))
    }
    @Test fun bluetoothServiceAndOemAliasesOpenBluetoothPlayerWithoutAutoplay() {
        listOf(
            LauncherMediaState(sourcePackage="com.android.bluetooth"),
            LauncherMediaState(oemSource="SYSTEM_BT_MUSIC"),
            LauncherMediaState(oemSource="BT_MUSIC")
        ).forEach { state ->
            val intent= requireNotNull(LauncherSourceLaunch.sourceIntent(state))
            assertEquals("com.qinggan.app.music",intent.component?.packageName)
            assertEquals("com.qinggan.app.music.activity.MusicActivity",intent.component?.className)
            val extras=requireNotNull(intent.getBundleExtra("ASGARD_EXT_DATA"))
            assertEquals("BT_MUSIC",extras.getString("MEDIA_TYPE"))
            assertEquals("PAGE_HOME",extras.getString("PAGE_NAME"))
            assertFalse(extras.getBoolean("AUTO_PLAY"))
        }
    }
    @Test fun userMusicAppKeepsItsOwnLaunchPath() {
        assertNull(LauncherSourceLaunch.sourceIntent(LauncherMediaState(sourcePackage="com.apple.android.music")))
    }
}
