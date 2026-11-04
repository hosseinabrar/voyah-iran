package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.content.Intent
import android.os.Looper
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LauncherThemeObserverTest {
    private fun theme(value:String){
        Class.forName("android.os.SystemProperties").getMethod("set",String::class.java,String::class.java)
            .invoke(null,"persist.sys.qg.currenttheme",value)
    }
    @Test fun sharedObserverNotifiesOnlyChangesAndRecoversMissedBroadcast(){
        val context=RuntimeEnvironment.getApplication()
        theme("default")
        var first=0;var second=0
        val stopFirst=LauncherTheme.observe(context){first++}
        val stopSecond=LauncherTheme.observe(context){second++}
        try{
            assertFalse(LauncherTheme.isLight())
            theme("simple")
            context.sendBroadcast(Intent("theme_loaded"));shadowOf(Looper.getMainLooper()).idle()
            assertTrue(LauncherTheme.isLight());assertEquals(1,first);assertEquals(1,second)
            context.sendBroadcast(Intent("theme_loaded"));shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1,first)
            stopFirst()
            theme("default") // missed OEM event recovered by the single shared timer
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
            assertFalse(LauncherTheme.isLight());assertEquals(1,first);assertEquals(2,second)
        }finally{stopFirst();stopSecond();theme("default")}
    }
}
