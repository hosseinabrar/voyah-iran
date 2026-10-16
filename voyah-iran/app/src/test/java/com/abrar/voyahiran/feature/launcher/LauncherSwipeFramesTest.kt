package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherSwipeFramesTest {
    @Test fun recordsSlowIntervalsOnlyDuringCurrentGesture(){
        val s=LauncherSwipeFrames()
        s.frame(0);s.frame(16_000_000);s.frame(66_000_000)
        assertEquals(2,s.intervals);assertEquals(1,s.slow);assertEquals(50_000_000L,s.maxGapNanos)
        s.reset();s.frame(100_000_000)
        assertEquals(0,s.intervals);assertEquals(0,s.slow);assertEquals(0L,s.maxGapNanos)
    }
    @Test fun duplicateFrameDoesNotInventAnInterval(){
        val s=LauncherSwipeFrames();s.frame(1);s.frame(1)
        assertEquals(0,s.intervals)
    }
}
