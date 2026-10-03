package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class AvmNativeCommandTest {
    @Test fun threeDimensionalPacketsMatchSouthEastAndSouthWestCapture() {
        assertArrayEquals(byteArrayOf(0x1c,8,0xe0.toByte(),0x47,0x19,2,0,1,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.RIGHT,true,false))
        assertArrayEquals(byteArrayOf(0x1c,8,0xe0.toByte(),0x47,0x19,2,0,3,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.RIGHT,false,false))
        assertArrayEquals(byteArrayOf(0x1c,8,0x40,0x25,0xfd.toByte(),1,0,1,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.LEFT,true,false))
        assertArrayEquals(byteArrayOf(0x1c,8,0x40,0x25,0xfd.toByte(),1,0,3,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.LEFT,false,false))
    }
    @Test fun sidePacketsMatchRecordedPhysicalTaps() {
        assertArrayEquals(byteArrayOf(0x1c,8,0x60,0x20,0x5e,1,0,1,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.LEFT,true))
        assertArrayEquals(byteArrayOf(0x1c,8,0x60,0x20,0x5e,1,0,3,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.LEFT,false))
        assertArrayEquals(byteArrayOf(0x1c,8,0xe0.toByte(),0x48,0x68,1,0,1,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.RIGHT,true))
        assertArrayEquals(byteArrayOf(0x1c,8,0xe0.toByte(),0x48,0x68,1,0,3,0,0),AvmNativeCommand.sideTouch(AvmTurnPolicy.Side.RIGHT,false))
    }
    @Test fun closeMatchesPhysicalButtonCapture() {
        assertArrayEquals(byteArrayOf(0x1c,8,0,10,76,0,0,1,0,0),AvmNativeCommand.closeTouch(true))
        assertArrayEquals(byteArrayOf(0x1c,8,0,10,76,0,0,3,0,0),AvmNativeCommand.closeTouch(false))
    }
    @Test fun viewUsesNativeBitPositionAndPreservesAllOtherFields() {
        for(view in listOf(1,3,4,5,11,12)) {
            assertArrayEquals(byteArrayOf(0x1c,8,0,0,0,0,(view*2).toByte(),0,0,0),AvmNativeCommand.view(view))
        }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnrelatedNativeCommands() { AvmNativeCommand.view(99) }
}
