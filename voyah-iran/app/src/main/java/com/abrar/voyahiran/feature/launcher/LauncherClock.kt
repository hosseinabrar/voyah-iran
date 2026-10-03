package com.abrar.voyahiran.feature.launcher

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date

/** ICU's Persian calendar, including leap years. Use the vehicle's current timezone. */
object LauncherClock {
    fun persianDate(time: Long): String = SimpleDateFormat("EEEE، d MMMM yyyy", ULocale("fa_IR@calendar=persian")).format(Date(time))
    fun gregorianDate(time: Long): String = SimpleDateFormat("d MMMM yyyy", ULocale.ENGLISH).format(Date(time)).uppercase(java.util.Locale.ENGLISH)
}
