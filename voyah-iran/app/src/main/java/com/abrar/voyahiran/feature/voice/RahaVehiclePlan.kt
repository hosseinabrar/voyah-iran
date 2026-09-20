package com.abrar.voyahiran.feature.voice

import com.abrar.raha.RahaAction

/** Pure planning: resolve all reads/parameters before sending the first write. */
data class RahaWrite(val family: String, val field: String, val value: Int)
interface RahaVehicleSnapshot {
    fun speed(): Int
    fun vehicle(field: String): Int
    fun air(field: String): Int
    fun airValue(getter: String): Double
    fun light(getter: String): Int
    fun overseas(): Boolean
}

class RahaVehiclePlan(private val state: RahaVehicleSnapshot) {
    fun create(action: RahaAction, p: Map<String, Any>): List<RahaWrite> {
        val out = mutableListOf<RahaWrite>()
        fun v(field: String, value: Int) { out += RahaWrite("vehicle", field, value) }
        fun a(field: String, value: Int) { out += RahaWrite(if (field in ClimateStateController.feedback) "air-state" else "air", field, value) }
        fun on(key: String = "enabled") = p[key] as? Boolean ?: error("Missing boolean $key")
        fun text(key: String) = p[key] as? String ?: error("Missing $key")
        fun number(key: String) = (p[key] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: error("Missing number $key")
        if (action in setOf(RahaAction.OPEN_TAILGATE, RahaAction.CLOSE_TAILGATE, RahaAction.OPEN_FUEL_FLAP,
                RahaAction.OPEN_CHARGE_PORT, RahaAction.SET_PARKING_BRAKE)) {
            require(state.speed() == 0) { "This operation requires a confirmed stationary vehicle" }
        }
        fun range(value: Int, low: Int, high: Int) = value.also { require(it in low..high) { "Invalid vehicle value: $it" } }
        fun level(value: Any?, max: Int): Int = when(value) {
            "max", "high" -> max
            "min", "low" -> 1
            "medium" -> 2
            is Number -> range(value.toInt(), 1, max)
            else -> error("Missing level")
        }
        fun seats(): List<String> = when (p["seat"] ?: "driver") {
            "driver" -> listOf("LEFT"); "passenger" -> listOf("RIGHT"); "front" -> listOf("LEFT", "RIGHT")
            else -> error("Unsupported seat")
        }
        fun temperatures(change: (Double) -> Double) {
            val sides = when (p["zone"] ?: "front") {
                "front" -> listOf("Left", "Right"); "driver" -> listOf("Left"); "passenger" -> listOf("Right")
                else -> error("Unsupported climate zone")
            }
            for (side in sides) {
                val current = state.airValue("getAir${side}Temperature")
                require(current in 15.5..30.5) { "Temperature unavailable: $current" }
                val target = change(current).coerceIn(15.5, 30.5)
                if (target != current) a("AC_${side.uppercase()}_TEMP", (target * 10).toInt())
            }
        }
        fun temperature() = temperatures { current ->
            when {
                p["temperatureLevel"] == "max" -> 30.5
                p["temperatureLevel"] == "min" -> 15.5
                p.containsKey("temperatureMaxC") -> minOf(current, number("temperatureMaxC"))
                p.containsKey("temperatureMinC") -> maxOf(current, number("temperatureMinC"))
                p.containsKey("degreesC") -> number("degreesC")
                else -> current + number("delta")
            }
        }
        when (action) {
            RahaAction.SET_CLIMATE_POWER -> {
                a("AC_POWER_SWITCH", if (on()) 1 else 0)
                if (on()) {
                    (p["acEnabled"] as? Boolean)?.let { a("AC_SWITCH", if (it) 1 else 0) }
                    if (p.keys.any { it.startsWith("temperature") }) temperature()
                }
            }
            RahaAction.SET_AC_POWER -> { if (on()) a("AC_POWER_SWITCH", 1); a("AC_SWITCH", if (on()) 1 else 0) }
            RahaAction.SET_MAX_COOLING -> {
                require(on()) { "H97C has no independent MAX AC switch to disable; choose temperature/fan or automatic climate" }
                a("AC_POWER_SWITCH", 1); a("AC_AUTO", 0); a("AC_SWITCH", 1)
                temperatures { 15.5 }; a("AC_BLOWER", 8); a("AC_RECIRC_AIR", 0)
            }
            RahaAction.SET_CLIMATE_AUTO -> { if (on()) a("AC_POWER_SWITCH", 1); a("AC_AUTO", if (on()) 1 else 0) }
            RahaAction.SET_CABIN_TEMPERATURE, RahaAction.ADJUST_CABIN_TEMPERATURE -> temperature()
            RahaAction.SET_FAN_SPEED, RahaAction.ADJUST_FAN_SPEED -> {
                // H97C feedback is 0..7; AC_BLOWER requests are 1..8.
                val n = if (action == RahaAction.SET_FAN_SPEED) (level(p["level"], 8) + 1).coerceAtMost(8)
                    else (range(state.airValue("getAirWindSpeed").toInt(), 0, 7) + 1 + number("delta").toInt()).coerceIn(1, 8)
                a("AC_POWER_SWITCH", 1); a("AC_BLOWER", n)
            }
            RahaAction.SET_AIRFLOW_DIRECTION -> {
                val n = mapOf("face" to 0, "faceAndFeet" to 1, "feet" to 2, "windshieldAndFeet" to 3, "windshield" to 4, "all" to 6).getValue(text("direction"))
                a("AC_POWER_SWITCH", 1); a("AC_FLOW_MODE", n)
            }
            RahaAction.SET_AIR_RECIRCULATION -> a("AC_RECIRC_AIR", mapOf("internal" to 0, "external" to 1, "auto" to 2).getValue(text("mode")))
            RahaAction.SET_FRONT_DEFROSTER -> a("AC_FRONT_DEFROST_SWITCH", if (on()) 1 else 0)
            RahaAction.SET_REAR_DEFROSTER -> a("AC_REAR_DEFROST_SWITCH", if (on()) 1 else 0)
            RahaAction.SET_FRONT_CLIMATE_SYNC -> a("AC_DUAL", if (on()) 0 else 1)
            RahaAction.SET_SEAT_HEATING, RahaAction.SET_SEAT_VENTILATION, RahaAction.SET_SEAT_MASSAGE,
            RahaAction.ADJUST_SEAT_HEATING, RahaAction.ADJUST_SEAT_VENTILATION, RahaAction.ADJUST_SEAT_MASSAGE -> {
                val type = when(action) {
                    RahaAction.SET_SEAT_HEATING, RahaAction.ADJUST_SEAT_HEATING -> "HEATING"
                    RahaAction.SET_SEAT_VENTILATION, RahaAction.ADJUST_SEAT_VENTILATION -> "VENTILATION"
                    else -> "MASS"
                }
                for (side in seats()) {
                    val switch = "FRONT_SEAT_${type}_SWITCH_$side"
                    val command = "FRONT_SEAT_${type}_${if (type == "MASS") "INTEN" else "COMMAND"}_$side"
                    val enabled = p["enabled"] != false
                    if (!enabled) v(switch, 1) else {
                        val n = when {
                            p.containsKey("delta") -> (range(state.vehicle(command), 0, 3) + number("delta").toInt()).coerceIn(1, 3)
                            p.containsKey("level") -> level(p["level"], 3)
                            else -> range(state.vehicle(command), 0, 3).takeIf { it > 0 } ?: 3
                        }
                        v(command, n)
                    }
                }
            }
            RahaAction.SET_SEAT_MASSAGE_PATTERN -> {
                val mode = when(text("pattern")) { "wave" -> 1; "rolling" -> 2; else -> error("OEM exposes wave and rolling massage only") }
                for (side in seats()) v("FRONT_SEAT_MASS_COMMAND_$side", mode)
            }
            RahaAction.SET_FRAGRANCE_POWER, RahaAction.SET_FRAGRANCE_CHANNEL, RahaAction.SET_FRAGRANCE_INTENSITY,
            RahaAction.ADJUST_FRAGRANCE_INTENSITY, RahaAction.SET_FRAGRANCE_DURATION, RahaAction.CONFIGURE_FRAGRANCE -> {
                if (p.containsKey("channel")) v("IVI_FRAG_TASTE", range(number("channel").toInt(), 1, 3))
                if (p.containsKey("intensity")) v("IVI_FRAG_CONCERNTION", level(p["intensity"], 3))
                if (p.containsKey("delta")) v("IVI_FRAG_CONCERNTION", (range(state.vehicle("IVI_FRAG_CONCERNTION_FB"), 1, 3) + number("delta").toInt()).coerceIn(1, 3))
                if (p.containsKey("durationMinutes")) v("FCM_DURATION_CONTROL", mapOf(0 to 0, 30 to 1, 60 to 2).getValue(number("durationMinutes").toInt()))
                if (p.containsKey("enabled")) v("FCM_SW_REQ", if (on()) 2 else 1)
            }
            RahaAction.SET_DOOR_LOCK -> { require(p["door"] == "all"); v("CENTRAL_LOCK_CONTROL", if (on("locked")) 2 else 1) }
            RahaAction.SET_CHILD_LOCK -> {
                // This vehicle has one combined control, regardless of the spoken side.
                v("CHILD_LOCK_CONTROL", if (on()) 2 else 1)
            }
            RahaAction.SET_WINDOW_POSITION, RahaAction.ADJUST_WINDOW_POSITION -> {
                for (field in WindowPositionController.fields(text("window"))) {
                    // Car-test calibration: half-open uses 40; small relative requests use half the previous step.
                    val target = if (action == RahaAction.SET_WINDOW_POSITION) {
                        val requested = range(number("percent").toInt(), 0, 100)
                        if (requested == 50) 40 else requested
                    } else {
                        val step = number("deltaPercent").toInt() / 2
                        (range(state.vehicle(WindowPositionController.feedback.getValue(field)), 0, 100) + step).coerceIn(0, 100)
                    }
                    out += RahaWrite("window-position", field, target)
                }
            }
            RahaAction.OPEN_WINDOW, RahaAction.CLOSE_WINDOW, RahaAction.VENT_WINDOW -> {
                val field = mapOf("driver" to "DRIVER_WINDOW_CONTROL", "passenger" to "PAS_WIDOW_CONTROL", "rearLeft" to "LEFT_BACK_WINDOW_CONTROL",
                    "rearRight" to "RIGHT_BACK_WINDOW_CONTROL", "all" to "ALL_WINDOW_CONTROL", "front" to "ALL_FRONT_WINDOW_CONTROL",
                    "rear" to "ALL_REAR_WINDOW_CONTROL", "left" to "ALL_LEFT_WINDOW_CONTROL", "right" to "ALL_RIGHT_WINDOW_CONTROL").getValue(text("window"))
                v(field, when(action) { RahaAction.OPEN_WINDOW -> 3; RahaAction.CLOSE_WINDOW -> 1; RahaAction.VENT_WINDOW -> 6 })
            }
            RahaAction.OPEN_SUNROOF, RahaAction.CLOSE_SUNROOF, RahaAction.SET_SUNROOF_POSITION, RahaAction.ADJUST_SUNROOF_POSITION,
            RahaAction.SET_SUNSHADE_POSITION, RahaAction.ADJUST_SUNSHADE_POSITION -> {
                val shade = action in setOf(RahaAction.SET_SUNSHADE_POSITION, RahaAction.ADJUST_SUNSHADE_POSITION)
                val field = if (shade) "ROLL_OPEN_PERCENT" else "SUNROOF_OPEN_PERCENT"
                val n = when(action) {
                    RahaAction.OPEN_SUNROOF -> 100; RahaAction.CLOSE_SUNROOF -> 0
                    RahaAction.ADJUST_SUNSHADE_POSITION, RahaAction.ADJUST_SUNROOF_POSITION -> (range(state.vehicle(field), 0, 100) + number("deltaPercent").toInt() * (if (shade) 1 else 2)).coerceIn(0, 100)
                    else -> range(number(if (shade) "position" else "percent").toInt(), 0, 100)
                }
                v(field, n)
            }
            RahaAction.OPEN_TAILGATE -> v("VEHICLE_TUNK_CONTROL_SWITCH", 2)
            RahaAction.CLOSE_TAILGATE -> v("VEHICLE_TUNK_CONTROL_SWITCH", 1)
            RahaAction.OPEN_CHARGE_PORT -> v("IVI_CHRG_PORT_CAP", 1)
            RahaAction.OPEN_FUEL_FLAP -> v("IVI_FUEL_PORT_CAP", 1)
            RahaAction.SET_REGENERATION_LEVEL, RahaAction.ADJUST_REGENERATION_LEVEL -> {
                val n = if (action == RahaAction.SET_REGENERATION_LEVEL) level(p["level"], 3) + 1
                    else (range(state.vehicle("HUM_ENERGY_PTREGEN_LEVL_FB"), 2, 4) + number("delta").toInt()).coerceIn(2, 4)
                v("HUM_ENERGY_PTREGEN_LEVL", n)
            }
            RahaAction.SET_SUSPENSION_HEIGHT -> {
                // China AirsuspensionCardLargeBindingImpl: low -> Sport, high -> Outing.
                // Normal-height modes are Eco/Comfort/Snow/Personal; no Lowest request exists here.
                val selected = text("level")
                require(selected in setOf("high", "medium", "low")) { "Unsupported suspension height" }
                val mode = when(selected) {
                    "high" -> {
                        require(state.speed() in 0..39) { "OEM Outing height requires speed below 40 km/h" }
                        4
                    }
                    "low" -> 3
                    else -> state.vehicle("DRIVING_MODE_SET").takeIf { it in setOf(1, 2, 5, 6) } ?: 2
                }
                out += RahaWrite("drive", "mode", mode)
            }
            RahaAction.SET_HIGHWAY_SUSPENSION_ADAPTATION -> v("HUM_HIGHWAY_MODE_REQ", if (on()) 2 else 1)
            RahaAction.SET_AUTO_HOLD -> v("AVH_MODE_SET", mapOf("automatic" to 1, "deepPedal" to 2).getValue(text("mode")))
            RahaAction.SET_HILL_DESCENT_CONTROL -> v("HDC_SWITCH", if (on()) 1 else 2)
            RahaAction.SET_STABILITY_CONTROL -> v("ESC_SWITCH", if (on()) 2 else 1)
            RahaAction.SET_PARKING_BRAKE -> v("EPB_PARK_SWITCH", if (on("engaged")) 1 else 2)
            RahaAction.SET_WIPER_SENSITIVITY, RahaAction.ADJUST_WIPER_SENSITIVITY -> {
                val n = if (action == RahaAction.SET_WIPER_SENSITIVITY) level((p["level"] as? Number)?.toInt()?.coerceIn(1, 4) ?: p["level"], 4)
                    else (range(state.vehicle("FRONT_WASHER_WIPER_SENSITIVITY"), 1, 4) + number("delta").toInt()).coerceIn(1, 4)
                v("FRONT_WASHER_WIPER_SENSITIVITY", n)
            }
            RahaAction.SET_HEADLIGHT_MODE, RahaAction.SET_DAYTIME_RUNNING_LIGHTS -> {
                // OEM LightFragment: auto=0, low beam=1, position/DRL=2, off=3.
                // Compare the selected mode, not actual lamp illumination in AUTO.
                val mode = if (action == RahaAction.SET_DAYTIME_RUNNING_LIGHTS) {
                    if (on()) "position" else "off"
                } else text("mode")
                val requested = mapOf("auto" to 0, "lowBeam" to 1, "position" to 2, "off" to 3).getValue(mode)
                val field = mapOf("off" to "OUT_LAMP_OFF", "auto" to "AUTO_LAMP_SWITCH", "lowBeam" to "LOW_BEAM_SWITCH", "position" to "POSITION_LAMP_SWITCH").getValue(mode)
                if (range(state.vehicle("LAMP_SWITCH_POSITION_FB"), 0, 3) != requested) v(field, 1)
            }
            RahaAction.SET_AUTO_HIGH_BEAM -> v("IHBC_SWITCH", if (on()) 1 else 2)
            RahaAction.SET_REAR_FOG_LIGHT -> {
                val current = range(state.light("getFogRear"), 0, 1)
                if ((current == 1) != on()) v("REAR_FOG_LIGHT_SWITCH", 1)
            }
            RahaAction.SET_AMBIENT_LIGHT_POWER -> v("VEHICLE_AMBIENT_LIGHT_SWITCH", if (on()) 2 else 1)
            RahaAction.SET_AMBIENT_LIGHT_MODE -> {
                val mode = text("mode")
                require(mode in setOf("normal", "music", "driveMode"))
                out += RahaWrite("qg", "vehicle_setting_drive_mode_atm", if (mode == "driveMode") 1 else 0)
                v("VEHICLE_AMBIENT_LIGHT_SWITCH", if (p["enabled"] == false) 1 else 2)
                if (mode == "driveMode") v("VEHICLE_AMBIENT_LIGHT_COLOR", mapOf(1 to 19, 2 to 5, 3 to 64, 4 to 54, 5 to 10, 6 to 9).getValue(state.vehicle("DRIVING_MODE_SET")))
                v("VEHICLE_AMBIENT_LIGHT_MUSICAL_RHYTHM_SWITCH", if (mode == "music") 2 else 1)
            }
            RahaAction.SET_AMBIENT_LIGHT_BRIGHTNESS, RahaAction.ADJUST_AMBIENT_LIGHT_BRIGHTNESS -> {
                val n = if (p.containsKey("level")) level(p["level"], 100)
                    else if (p.containsKey("percent")) range(number("percent").toInt(), 0, 100)
                    else (range(state.vehicle("VEHICLE_AMBIENT_LIGHT_BRIGHTNESS_LEVEL"), 1, 10) * 10 + number("delta").toInt() * 20).coerceIn(0, 100)
                v("VEHICLE_AMBIENT_LIGHT_BRIGHTNESS_LEVEL", ((n + 5) / 10).coerceIn(1, 10))
            }
            RahaAction.SET_DRIVE_MODE -> out += RahaWrite("drive", "mode", mapOf("eco" to 1, "comfort" to 2, "sport" to 3, "outing" to 4, "snow" to 6, "personal" to 5).getValue(text("mode")))
            RahaAction.SAVE_SEAT_POSITION, RahaAction.RECALL_SEAT_POSITION -> error("Seat memory must be dispatched through OemSeatMemory, not raw CAN")
            RahaAction.SET_RAIN_SENSING_WIPERS -> error("Rain-sensing mode is not exposed by the supplied H97C menu interface")
            RahaAction.SET_MIRROR_FOLD -> error("Direct mirror folding is not confirmed on H97; automatic-fold configuration will not be changed instead")
            RahaAction.SET_ENERGY_STRATEGY, RahaAction.SET_BATTERY_RESERVE_TARGET, RahaAction.STOP_CHARGING,
            RahaAction.SET_BATTERY_PRECONDITIONING, RahaAction.ACTIVATE_SCENE, RahaAction.DEACTIVATE_SCENE,
            RahaAction.SET_POWER_KEEP_DURATION, RahaAction.SET_POWER_KEEP_ENGINE_CHARGING ->
                error("This action must use the installed OEM Settings adapter")
        }
        return out
    }
}
