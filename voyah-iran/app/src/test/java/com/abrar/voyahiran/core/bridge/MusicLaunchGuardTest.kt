package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class MusicLaunchGuardTest {
    @Test fun startupAndEmptyCardsShareBackgroundPath() {
        val root=java.io.File("src/main/java/com/abrar/voyahiran")
        val startup=root.resolve("feature/media/MusicAutomation.kt").readText()
        val control=root.resolve("feature/media/RahaMediaControl.kt").readText()
        assertTrue(startup.contains("RahaMediaControl.playDefault(context,0)"))
        assertFalse(startup.contains("startActivity("))
        assertFalse(control.contains("DefaultAppLaunch.execute"))
        assertTrue(control.indexOf("browser.connect()")<control.indexOf("context.startActivity("))
        assertTrue(control.contains("makeTaskLaunchBehind().apply {setLaunchDisplayId(displayId)}"))
        assertTrue(control.contains("kotlinx.coroutines.NonCancellable"))
        for(file in listOf("feature/launcher/VoyahLauncherActivity.kt","feature/launcher/PassengerLauncherActivity.kt","feature/media/SteeringMediaListener.kt"))
            assertTrue(root.resolve(file).readText().contains("RahaMediaControl.playDefault"))
    }
    @Test fun restoresOnlyPlayerAndKeepsWatchingPreviousScreenForLateSplash() {
        assertEquals("restore",MusicLaunchGuard.decision("player","player","launcher"))
        assertEquals("wait",MusicLaunchGuard.decision("launcher","player","launcher"))
        assertEquals("cancel",MusicLaunchGuard.decision("navigation","player","launcher"))
        assertEquals("cancel",MusicLaunchGuard.decision(null,"player","launcher"))
    }
    @Test fun limitsWindowCommandsToPhysicalDisplaysAndValidTokens() {
        for(display in listOf("0","1")) assertNotNull(BridgePolicy.resolve(listOf("music-launch-window","begin","com.apple.android.music",display)))
        assertNull(BridgePolicy.resolve(listOf("music-launch-window","begin","com.apple.android.music","7")))
        assertNull(BridgePolicy.resolve(listOf("music-launch-window","hide","invalid")))
        assertNotNull(BridgePolicy.resolve(listOf("music-launch-window","hide","12345678-1234-1234-1234-123456789abc")))
    }
}
