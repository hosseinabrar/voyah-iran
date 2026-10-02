package com.abrar.voyahiran.core.bridge

/** H97X Baidu AVM channel, verified against the native receiver and captured vehicle packets. */
internal data class AvmNativeFrame(val active: Boolean, val twoDimensional: Boolean, val view: Int) {
    /** Native getViewSelect: 2D N/W/E=1/3/4; 3D N/SW/SE=5/11/12. */
    fun targetView(side: AvmTurnPolicy.Side): Int = when (side) {
        AvmTurnPolicy.Side.LEFT -> if (twoDimensional) 3 else 11
        AvmTurnPolicy.Side.RIGHT -> if (twoDimensional) 4 else 12
        AvmTurnPolicy.Side.FRONT -> if (twoDimensional) 1 else 5
    }

    companion object {
        fun decode(data: ByteArray, count: Int): AvmNativeFrame? {
            if (count != 10 || data.size < count || (data[0].toInt() and 255) != 0x8f || data[1].toInt() != 8) return null
            val state = data[2].toInt() and 3
            val mode = data[3].toInt() and 3
            if (state !in 1..2 || mode !in 0..1) return null
            return AvmNativeFrame(state == 2, mode == 1, data[5].toInt() and 255)
        }
    }
}
