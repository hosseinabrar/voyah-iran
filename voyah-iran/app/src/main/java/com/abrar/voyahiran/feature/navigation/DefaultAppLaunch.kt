package com.abrar.voyahiran.feature.navigation

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.abrar.voyahiran.core.shell.CommandResult

internal object DefaultAppLaunch {
    fun execute(context: Context, action: String, requiredApp: String?): CommandResult {
        if (action == "OPEN_OEM_RADIO") {
            // Same explicit FM-page intent as the OEM MediaJumpUtils.toRadioPage.
            val intent = Intent().setClassName("com.qinggan.app.radio", "com.qinggan.app.radio.view.RadioMainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("ASGARD_EXT_DATA", android.os.Bundle().apply {
                    putString("APP_NAME", "radio_page_fm")
                    putString("PAGE_NAME", "radio_page_fm")
                    putBoolean("AUTO_PLAY", false)
                })
            check(context.packageManager.resolveActivity(intent, 0) != null) { "برنامهٔ رادیوی خودرو در دسترس نیست" }
            context.startActivity(intent, ActivityOptions.makeBasic().apply { launchDisplayId = 0 }.toBundle())
            return CommandResult(0, "OEM radio launched")
        }
        val prefs = NavigationPreferences(context)
        val selected = when (action) {
            "OPEN_DEFAULT_MAP" -> prefs.navigator
            "OPEN_DEFAULT_MUSIC" -> prefs.musicPlayer
            else -> error("Unsupported app command")
        }
        check(selected.isNotBlank()) { "ابتدا برنامهٔ پیش‌فرض را انتخاب کنید" }
        check(requiredApp == null || (requiredApp == "neshan" && selected == NavigationPreferences.NESHAN)) {
            "نشان نقشهٔ پیش‌فرض نیست"
        }
        val pkg = selected.substringBefore('#')
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("برنامهٔ پیش‌فرض نصب نیست یا قابل اجرا نیست")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, ActivityOptions.makeBasic().apply { launchDisplayId = 0 }.toBundle())
        return CommandResult(0, "Default application launched: $pkg")
    }
}
