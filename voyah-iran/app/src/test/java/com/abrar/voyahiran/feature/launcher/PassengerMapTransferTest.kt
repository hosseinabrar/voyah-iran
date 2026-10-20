package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class PassengerMapTransferTest {
    @Test fun returningFromPassengerCoversSourceAndCentralOpeningCoversDestination() {
        assertEquals(1,LauncherMapPreview.transferMaskDisplay(7,"MAP_DISPLAY=1 MAP_VISIBLE=1 MAP_FITTED=1"))
        assertEquals(0,LauncherMapPreview.transferMaskDisplay(0,"MAP_DISPLAY=7 MAP_VISIBLE=1"))
        assertNull(LauncherMapPreview.transferMaskDisplay(7,"MAP_DISPLAY=7 MAP_VISIBLE=1"))
        assertNull(LauncherMapPreview.transferMaskDisplay(7,"MAP_DISPLAY=1 MAP_VISIBLE=0"))
    }
    @Test fun readinessMustMatchDestinationDisplay() {
        assertTrue(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=1 MAP_VISIBLE=1 MAP_FITTED=1",1))
        assertFalse(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=7 MAP_VISIBLE=1 MAP_FITTED=1",1))
        assertFalse(LauncherMapPreview.isVisibleHere("MAP_DISPLAY=1 MAP_VISIBLE=0 MAP_FITTED=1",1))
    }
    @Test fun drawerHandoffUsesDestinationContextAndWaitsBeforeFade() {
        val root=java.io.File("src/main/java/com/abrar/voyahiran")
        val mask=root.resolve("feature/launcher/LauncherTransferMask.kt").readText()
        assertTrue(mask.contains("createDisplayContext(display)"))
        assertTrue(mask.contains("View(displayContext)"))
        assertTrue(mask.contains("displayContext.getSystemService"))
        val drawer=root.resolve("feature/launcher/LauncherAppsActivity.kt").readText()
        assertTrue(drawer.contains("LauncherMapPreview.openHostedMapOnPassenger"))
        val favorites=root.resolve("feature/launcher/PassengerLauncherActivity.kt").readText().substringAfter("private fun openApp(").substringBefore("private var catalogJob")
        assertTrue(favorites.contains("LauncherMapPreview.openHostedMapOnPassenger"))
        assertTrue(favorites.contains("if(openingApp)return"))
        val move=root.resolve("feature/launcher/LauncherMapPreview.kt").readText().substringAfter("private fun move(").substringBefore("private suspend fun transferWithBusyRetry")
        assertTrue(move.indexOf("mask!!.show()")<move.indexOf("transferWithBusyRetry(command,id)"))
        assertTrue(move.indexOf("awaitCardReady(id)")<move.indexOf("destinationReady=true"))
        assertTrue(move.contains("mask?.finish(destinationReady)"))
        val transfer=root.resolve("feature/launcher/LauncherMapPreview.kt").readText().substringAfter("internal suspend fun openHostedMapOnPassenger").substringBefore("/** Drawer launches")
        assertTrue(transfer.indexOf("mask.show()")<transfer.indexOf("launch()"))
        assertTrue(transfer.contains("stable>=3"))
        assertTrue(transfer.contains("mask.finish(ready)"))
        assertTrue(transfer.contains("withTimeoutOrNull(8000)"))
    }
}
