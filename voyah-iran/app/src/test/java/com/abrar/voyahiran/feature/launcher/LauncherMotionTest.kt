package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherMotionTest {
    @Test fun selectionUsesOemOvershootAndSettlesAt400ms() {
        val v=MotionValue(0f);v.set(1f,0,true,true)
        assertEquals(0f,v.value(0),0f)
        assertTrue(v.value(300)>1f)
        assertEquals(1f,v.value(400),0f)
        assertFalse(v.moving(400))
    }
    @Test fun repeatedCanConfirmationDoesNotRestartAnimation() {
        val v=MotionValue(0f);v.set(2f,0,true,true)
        val before=v.value(180);v.set(2f,180,true,true)
        assertEquals(before,v.value(180),0f)
        assertFalse(v.moving(400))
    }
    @Test fun rapidReversalStartsAtCurrentPosition() {
        val v=MotionValue(0f);v.set(2f,0,true,true)
        val shown=v.value(100);v.set(0f,100,true,true)
        assertEquals(shown,v.value(100),0f)
        assertEquals(0f,v.value(500),0f)
    }
    @Test fun hiddenOrDisabledMotionSnapsAndStops() {
        val v=MotionValue(0f);v.set(2f,0,true);v.set(3f,100,false)
        assertEquals(3f,v.value(100),0f);assertFalse(v.moving(100))
        v.set(4f,100,true);v.finish()
        assertEquals(4f,v.value(100),0f);assertFalse(v.moving(100))
    }
}
