package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class AvmNativeFrameTest {
    private fun packet(text: String) = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun decode(text: String) = packet(text).let { AvmNativeFrame.decode(it,it.size) }
    @Test fun recordedThreeDimensionalFrames() {
        assertEquals(AvmNativeFrame(true,false,7),decode("8f 08 02 c4 03 07 1c 00 c0 1f"))
        assertEquals(AvmNativeFrame(true,false,7),decode("8f 08 02 c4 03 07 1c 00 40 be"))
    }
    @Test fun recordedTwoDimensionalFrames() {
        assertEquals(AvmNativeFrame(true,true,3),decode("8f 08 02 c5 03 03 1c 00 40 bc"))
        assertEquals(AvmNativeFrame(true,true,3),decode("8f 08 02 c5 03 03 1c 00 80 c3"))
    }
    @Test fun recordedSideSelectionsAndClose() {
        assertEquals(AvmNativeFrame(true,true,4),decode("8f 08 02 c5 03 04 1c 00 20 f9"))
        assertEquals(AvmNativeFrame(false,true,4),decode("8f 08 01 c1 03 04 1c 00 90 84"))
    }
    @Test fun rejectsUnrelatedOrIncompleteMessages() {
        assertNull(decode("01 01 01 00 00 00 00 00 00 00"))
        assertNull(decode("8f 07 02 c5 03 03 1c 00 80 c3"))
        assertNull(decode("8f 08 02 c5"))
        assertNull(AvmNativeFrame.decode(ByteArray(4),10))
    }
    @Test fun rejectsInvalidAvmAndModeStates() {
        assertNull(decode("8f 08 ff ff ff ff ff ff ff ff"))
        assertNull(decode("8f 08 02 c6 03 03 1c 00 80 c3"))
    }
    @org.junit.Test fun selectsLateralViewsIn2dAndRearDiagonalsIn3d() {
        val flat=AvmNativeFrame(true,true,1)
        val spatial=AvmNativeFrame(true,false,5)
        org.junit.Assert.assertEquals(3,flat.targetView(AvmTurnPolicy.Side.LEFT))
        org.junit.Assert.assertEquals(4,flat.targetView(AvmTurnPolicy.Side.RIGHT))
        org.junit.Assert.assertEquals(11,spatial.targetView(AvmTurnPolicy.Side.LEFT))
        org.junit.Assert.assertEquals(12,spatial.targetView(AvmTurnPolicy.Side.RIGHT))
        org.junit.Assert.assertEquals(1,flat.targetView(AvmTurnPolicy.Side.FRONT))
        org.junit.Assert.assertEquals(5,spatial.targetView(AvmTurnPolicy.Side.FRONT))
        org.junit.Assert.assertTrue(flat.twoDimensional)
        org.junit.Assert.assertFalse(spatial.twoDimensional)
    }
}
