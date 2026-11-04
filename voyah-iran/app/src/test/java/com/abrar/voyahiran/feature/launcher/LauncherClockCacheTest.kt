package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherClockCacheTest {
    @Test fun dateCacheHandlesMidnightBackwardTimeAndTimezoneChanges(){
        val original=TimeZone.getDefault()
        try{
            for(zone in listOf("Asia/Tehran","America/New_York","UTC")){
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for(time in listOf(1793796840000L,1793883240000L,1793796840000L,0L)){
                    val p=SimpleDateFormat("EEEE، d MMMM yyyy",ULocale("fa_IR@calendar=persian"))
                    val g=SimpleDateFormat("d MMMM yyyy",ULocale.ENGLISH)
                    val tz=android.icu.util.TimeZone.getTimeZone(zone);p.timeZone=tz;g.timeZone=tz
                    assertEquals(p.format(Date(time)),LauncherClock.persianDate(time))
                    assertEquals(g.format(Date(time)).uppercase(java.util.Locale.ENGLISH),LauncherClock.gregorianDate(time))
                    assertSame(LauncherClock.persianDate(time),LauncherClock.persianDate(time+1000))
                }
            }
        }finally{TimeZone.setDefault(original)}
    }
}
