package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherArtworkIdentityTest {
    @Test fun reparceledArtworkDoesNotCountAsNewCover() {
        val first=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val copy=first.copy(Bitmap.Config.ARGB_8888,true)!!
        assertNotSame(first,copy)
        assertTrue(LauncherArtworkIdentity.same(first,copy))
        copy.setPixel(8,8,Color.RED)
        assertFalse(LauncherArtworkIdentity.same(first,copy))
        assertFalse(LauncherArtworkIdentity.same(first,null))
        assertTrue(LauncherArtworkIdentity.same(null,null))
    }
}
