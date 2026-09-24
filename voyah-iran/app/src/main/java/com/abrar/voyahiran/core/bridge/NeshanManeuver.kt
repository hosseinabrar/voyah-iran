package com.abrar.voyahiran.core.bridge

/** Raw cluster IDs verified by the user on the vehicle, 2026-09-21.
 * Mirrors Neshan t10.g icon selection for ordinary navigation (special roundabout icons).
 * turnIndex is StepInfo.j(), NOT the route step index or an assumed exit ordinal.
 */
internal object NeshanManeuver {
    // Missing street names must not suppress the destination icon or remaining distance.
    fun roadLabel(type: String, road: String): String = road.trim().ifEmpty {
        "-"
    }
    fun availableIcon(type: String, modifier: String, turnIndex: Int? = null): Int =
        if (type in setOf("ARRIVE", "DEPART", "ROUNDABOUT", "ROTARY", "NEW_EXIT_ROUNDABOUT") ||
            modifier in setOf("LEFT", "SHARP_LEFT", "SLIGHT_LEFT", "RIGHT", "SHARP_RIGHT", "SLIGHT_RIGHT", "STRAIGHT", "U_TURN"))
            icon(type, modifier, turnIndex) else 0
    fun icon(type: String, modifier: String, turnIndex: Int? = null): Int {
        when (type) {
            "ARRIVE", "DEPART" -> return 24
            "ROUNDABOUT" -> return 9
            "NEW_EXIT_ROUNDABOUT" -> return 12
            "ROTARY" -> when (turnIndex ?: 0) {
                0 -> return 9
                1 -> return 5
                2 -> return 4
                3 -> return 3
                4 -> return 2
                5 -> return 10
                6 -> return 8
                7 -> return 7
                8 -> return 6
            }
        }
        // Other maneuver types (including ON_RAMP/OFF_RAMP) use the direction,
        // just as Neshan does. Unknown direction falls back to straight in Neshan.
        return when (modifier) {
            "LEFT" -> 7
            "SHARP_LEFT" -> 6
            "SLIGHT_LEFT" -> 8
            "RIGHT" -> 3
            "SHARP_RIGHT" -> 4
            "SLIGHT_RIGHT" -> 2
            "U_TURN" -> 5
            else -> 1
        }
    }
}
