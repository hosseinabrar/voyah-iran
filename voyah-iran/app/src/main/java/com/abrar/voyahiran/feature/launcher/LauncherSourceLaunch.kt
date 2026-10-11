package com.abrar.voyahiran.feature.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.abrar.voyahiran.feature.navigation.DefaultAppLaunch

internal object LauncherSourceLaunch {
    fun open(context: Context, state: LauncherMediaState) {
        if (state.oemSource.isEmpty() && state.sourcePackage.isEmpty()) {
            DefaultAppLaunch.execute(context, "OPEN_DEFAULT_MUSIC", null)
            return
        }
        val oemPackage=sourcePackage(state.oemSource)
        val intent = sourceIntent(state) ?: context.packageManager.getLaunchIntentForPackage(oemPackage ?: state.sourcePackage)?.apply {
            if(oemPackage!=null)putExtra("ASGARD_EXT_DATA",Bundle().apply {
                putBoolean("AUTO_PLAY",false)
                if(state.oemSource in setOf("RDS","DAB"))putInt("PAGE_INDEX",-1)
            })
        }
            ?: error("Music source has no launchable activity")
        check(context.packageManager.resolveActivity(intent, 0) != null) { "Music source is not installed" }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().apply { launchDisplayId = 0 }.toBundle())
    }

    internal fun sourcePackage(source:String):String? = when(source) {
        "WECAR_FLOW" -> "com.tencent.wecarflow"
        "RDS" -> "com.pateo.rdsapp"
        "DAB" -> "com.qinggan.app.dab"
        "WANGYI_CLOUD" -> "com.qinggan.app.netease"
        "CLOUD_DESK" -> "com.reachauto.clouddesk"
        "LOCAL_VIDEO" -> "com.qinggan.usbvideo"
        else -> null
    }

    // Same explicit page bundle used by OEM MediaJumpUtils; opening does not start playback.
    internal fun sourceIntent(state: LauncherMediaState): Intent? {
        // Android's Bluetooth media session is a service, not a launchable player.
        // Both Bluetooth session variants belong to the OEM music page.
        val source = if(state.sourcePackage=="com.android.bluetooth" || state.oemSource=="SYSTEM_BT_MUSIC")
            "BT_MUSIC" else state.oemSource
        val component = when(source) {
            "LOCAL_RADIO", "XMLA_MUSIC", "RADIO_YUNTING" -> "com.qinggan.app.radio" to "com.qinggan.app.radio.view.RadioMainActivity"
            "BT_MUSIC", "USB_MUSIC", "KW_MUSIC" -> "com.qinggan.app.music" to "com.qinggan.app.music.activity.MusicActivity"
            else -> return null
        }
        val page = when(source) {
            "LOCAL_RADIO" -> if(state.artist.contains("AM", true)) "radio_page_am" else "radio_page_fm"
            "XMLA_MUSIC" -> "radio_page_xmly"
            "RADIO_YUNTING" -> "radio_page_yunting"
            else -> "PAGE_HOME"
        }
        return Intent().setClassName(component.first, component.second)
            .putExtra("ASGARD_EXT_DATA", Bundle().apply {
                putString("MEDIA_TYPE", source)
                putString("PAGE_NAME", page)
                putBoolean("AUTO_PLAY", false)
            })
    }
}
