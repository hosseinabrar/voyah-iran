package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherMediaContentTest {
    @Test fun smartShowsMapForEmptyMediaButNotForPausedContentAwayFromTurn() {
        assertTrue(ClusterMapMode.AUTO.showsMap(false, LauncherMediaState().hasContent))
        val paused=LauncherMediaState(title="Tamasha",playing=false)
        assertFalse(ClusterMapMode.AUTO.showsMap(false,paused.hasContent))
        assertTrue(ClusterMapMode.AUTO.showsMap(true,paused.hasContent))
        assertFalse(ClusterMapMode.AUTO.showsMap(false,LauncherMediaState(radio=true).hasContent))
        assertFalse(ClusterMapMode.AUTO.showsMap(false,null))
        assertTrue(ClusterMapMode.AUTO.showsMap(true,null))
        assertFalse(ClusterMapMode.OFF.showsMap(true,false))
        assertTrue(ClusterMapMode.ON.showsMap(false,true))
    }
    @Test fun pauseKeepsLauncherContentForCluster() {
        val memory=LauncherMediaMemory()
        memory.remember(LauncherMediaState(title="Tamasha",artist="Shadmehr Aghili",
            playing=true,position=72000,duration=180000,coverUri="content://art/1"))
        val paused=memory.paused(null,false)
        assertFalse(paused.playing)
        assertTrue(paused.hasContent)
        assertEquals("Tamasha",paused.title)
        assertEquals("content://art/1",paused.coverUri)
        assertEquals(72000L,paused.position)
        assertFalse(memory.paused(null,true).hasContent)
    }
    @Test fun onlyEmptyCardIsIdleRegardlessOfPlayback() {
        assertFalse(LauncherMediaState().hasContent)
        assertFalse(LauncherMediaState(source="Apple Music",sourcePackage="com.apple.android.music").hasContent)
        assertTrue(LauncherMediaState(title="Tamasha",playing=false).hasContent)
        assertTrue(LauncherMediaState(artist="Artist",playing=false).hasContent)
        assertTrue(LauncherMediaState(coverUri="content://art/1",playing=false).hasContent)
        assertTrue(LauncherMediaState(radio=true,playing=false).hasContent)
        assertTrue(LauncherMediaState(playing=true).hasContent)
    }
}
