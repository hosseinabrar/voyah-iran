package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.os.Binder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherAvmVisibilityTest {
    @Test fun readsOnlyOemVisibilityAndFailsClosedWhenUnavailable() {
        val reader=LauncherAvmVisibility(RuntimeEnvironment.getApplication())
        assertFalse(reader.visible())
        var state=1
        val binder=object:Binder() {
            init { attachInterface(null,"com.qinggan.system.ISystemPolicy") }
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                assertEquals(13,code)
                data.enforceInterface("com.qinggan.system.ISystemPolicy")
                reply!!.writeNoException();reply.writeInt(state)
                return true
            }
        }
        reader.javaClass.getDeclaredField("remote").apply{isAccessible=true}.set(reader,binder)
        assertTrue(reader.visible())
        state=0;assertFalse(reader.visible())
        state=-1;assertFalse(reader.visible())
        reader.stop();assertFalse(reader.visible())
    }
}
