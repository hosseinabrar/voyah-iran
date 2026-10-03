package com.abrar.voyahiran.core.bridge

/** H97X dls_reverse_svr SendViewSelect / SendTouchScreenEvent packet layout.
 * Only camera presentation messages; no generic raw CAN entry point is exposed.
 */
internal object AvmNativeCommand {
    fun view(value:Int):ByteArray {
        require(value in setOf(1,3,4,5,11,12))
        return ByteArray(10).apply { this[0]=0x1c; this[1]=8; this[6]=(value shl 1).toByte() }
    }
    fun closeTouch(down:Boolean):ByteArray = ByteArray(10).apply {
        this[0]=0x1c; this[1]=8
        // Verified close button: x=80,y=76 (physical-touch capture).
        this[2]=(80 shl 5).toByte(); this[3]=(80 shr 3).toByte()
        this[4]=76; this[7]=if(down) 1 else 3
    }
    /** Exact coordinates from alternating left/right physical taps in the 2026-10-02 capture. */
    fun sideTouch(side:AvmTurnPolicy.Side, down:Boolean, twoDimensional:Boolean = true):ByteArray {
        val (x,y)=when(side) {
            AvmTurnPolicy.Side.LEFT -> if(twoDimensional) 259 to 350 else 298 to 509
            AvmTurnPolicy.Side.RIGHT -> if(twoDimensional) 583 to 360 else 575 to 537
            AvmTurnPolicy.Side.FRONT -> error("Front touch position has not been verified")
        }
        return ByteArray(10).apply {
            this[0]=0x1c; this[1]=8
            this[2]=(x shl 5).toByte(); this[3]=(x shr 3).toByte()
            this[4]=y.toByte(); this[5]=((y shr 8) and 7).toByte()
            this[7]=if(down) 1 else 3
        }
    }
}
