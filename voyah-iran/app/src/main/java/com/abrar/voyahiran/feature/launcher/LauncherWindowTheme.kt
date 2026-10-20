package com.abrar.voyahiran.feature.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window

/** Window chrome follows the vehicle, independently of the installed-app theme preference. */
internal object LauncherWindowTheme {
    fun background(light: Boolean) = if(light) 0xffe5ebf3.toInt() else Color.BLACK

    @Suppress("DEPRECATION")
    fun apply(window: Window, light: Boolean) {
        val color=background(light)
        LauncherEntrance.theme(window,color)
        if(window.statusBarColor!=color) window.statusBarColor=color
        if(window.navigationBarColor!=color) window.navigationBarColor=color
        val flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val previous=window.decorView.systemUiVisibility
        val next=if(light) previous or flags else previous and flags.inv()
        if(next!=previous) window.decorView.systemUiVisibility=next
        if((window.decorView.background as? ColorDrawable)?.color!=color) {
            window.setBackgroundDrawable(ColorDrawable(color))
        }
    }
}
