package com.abrar.voyahiran.feature.voice

internal interface DriverAssistancePort {
    fun validate(field: String)
    fun read(field: String): Int
    fun write(field: String, value: Int)
    fun confirm(field: String, value: Int)
    fun pause(milliseconds: Long)
}

/** OEM request values are not interchangeable with booleans or camera feedback values. */
internal class DriverAssistanceExecutor(private val port: DriverAssistancePort, private val legacyLane: Boolean = false) {
    data class Change(val field: String, val value: Int, val feedback: String = field,
                      val expected: Int = value, val valid: IntRange = 1..2)

    fun execute(action: String, p: Map<String, Any>) {
        check(!legacyLane || action != "SET_LANE_DEPARTURE_ALERT_TYPE") { "Factory lane menu does not expose warning-type selection" }
        val changes = if (legacyLane && action == "SET_LANE_SUPPORT_MODE" && !p.containsKey("sensitivity")) {
            val field = "LDW_RDP_LKS_FUNC_ENABLE"
            port.validate(field)
            val current = port.read(field)
            listOf(Change(field, legacyLaneMode(p, current), valid = 1..4))
        } else plan(action, p)
        // Resolve and read the entire request before making a multi-field change.
        changes.forEach {
            port.validate(it.field)
            port.validate(it.feedback)
            check(port.read(it.feedback) in it.valid) { "Unavailable OEM state: ${it.feedback}" }
        }
        changes.forEachIndexed { index, change ->
            if (port.read(change.feedback) != change.expected) {
                port.write(change.field, change.value)
                port.confirm(change.feedback, change.expected)
            }
            if (index < changes.lastIndex) port.pause(300)
        }
    }

    companion object {
        /** 2023 menu uses one off/warning/keeping/centering state, no separate master switch. */
        fun legacyLaneMode(p: Map<String, Any>, current: Int): Int {
            require(current in 1..4) { "Lane mode unavailable" }
            if (p.keys == setOf("enabled") && p["enabled"] is Boolean)
                return if (p["enabled"] == false) 1 else if (current == 1) 2 else current
            require(p.keys == setOf("mode"))
            return mapOf("off" to 1,"warning" to 2,"keeping" to 3).getValue(p["mode"] as String)
        }

        private val toggles = mapOf(
            "SET_EASY_ENTRY" to "AUTO_EASY_ENTER_SET",
            "SET_EASY_EXIT" to "DSCU_EASY_ENTER_SET",
            "SET_PEDESTRIAN_WARNING_SOUND" to "HUM_VSP_FUNCTION_SW",
            "SET_DOOR_OPEN_WARNING" to "DOW_SYSTEM_SWITCH",
            "SET_FORWARD_COLLISION_WARNING" to "FCW_SWITCH",
            "SET_REAR_CROSS_TRAFFIC_WARNING" to "RCTA_SWITCH",
            "SET_REAR_COLLISION_WARNING" to "RCW_SWITCH",
            "SET_AUTOMATIC_EMERGENCY_BRAKING" to "AEB_SWITCH",
            "SET_BLIND_SPOT_MONITORING" to "BSD_SYSTEM_SWITCH",
            "SET_DRIVER_MONITORING" to "DMS_CAMERA_CTL",
            "SET_DRIVER_GESTURES" to "GESTUREID_REQ",
            "SET_LEAD_VEHICLE_DEPARTURE_ALERT" to "FVSR_SWITCH",
            "SET_FRONT_CROSS_TRAFFIC_WARNING" to "FCTA_SWITCH",
            "SET_TRAFFIC_SIGN_RECOGNITION" to "TSR_SWITCH"
        )
        val actions = toggles.keys + setOf("SET_LANE_SUPPORT_MODE", "SET_LANE_DEPARTURE_ALERT_TYPE")

        fun plan(action: String, p: Map<String, Any>): List<Change> {
            require(action in actions)
            if (p.containsKey("sensitivity")) {
                require(p.keys == setOf("sensitivity"))
                return when (action) {
                    "SET_LANE_SUPPORT_MODE" -> listOf(Change("LDW_LKS_SENSITIVITY",
                        mapOf("min" to 1, "max" to 2).getValue(p["sensitivity"] as String)))
                    "SET_FORWARD_COLLISION_WARNING" -> listOf(Change("FCW_SENSITIVITY",
                        mapOf("min" to 1, "medium" to 2, "max" to 3).getValue(p["sensitivity"] as String), valid = 1..3))
                    "SET_DRIVER_MONITORING" -> listOf(Change("DMS_WEARY_SET", 2), Change("DMS_SEN_SET",
                        mapOf("min" to 0, "max" to 1).getValue(p["sensitivity"] as String), valid = 0..1))
                    else -> error("Sensitivity is not supported for $action")
                }
            }
            if (action == "SET_LANE_SUPPORT_MODE") {
                if (p.containsKey("enabled")) {
                    require(p.keys == setOf("enabled") && p["enabled"] is Boolean)
                    return listOf(Change("LANE_ASSIST_SWITCH", if(p["enabled"] == true) 1 else 2))
                }
                require(p.keys == setOf("mode"))
                return when (p["mode"]) {
                    "off" -> listOf(Change("LANE_ASSIST_SWITCH", 2))
                    "warning", "keeping" -> listOf(Change("LANE_ASSIST_SWITCH", 1),
                        Change("LDW_RDP_LKS_FUNC_ENABLE", if (p["mode"] == "warning") 2 else 3, valid = 1..3))
                    else -> error("Unsupported lane mode: ${p["mode"]}")
                }
            }
            if (action == "SET_LANE_DEPARTURE_ALERT_TYPE") {
                require(p.keys == setOf("type"))
                return listOf(Change("LDW_WARNING_TYPE", mapOf("voice" to 1, "vibration" to 2).getValue(p["type"] as String)))
            }
            require(p.keys == setOf("enabled") && p["enabled"] is Boolean)
            val enabled = p["enabled"] as Boolean
            val field = toggles.getValue(action)
            // Entry is the suspension preference; exit is the OEM seat-courtesy
            // checkbox (SeatFrontManager.setEasyEnter). Both use 2=on, 1=off
            // and same-field feedback, never immediate movement requests.
            if (action == "SET_EASY_ENTRY" || action == "SET_EASY_EXIT")
                return listOf(Change(field, if (enabled) 2 else 1))
            // Camera control: 2=on, 1=off; camera STATUS: 1=on, 0=off.
            if (action == "SET_DRIVER_MONITORING") return listOf(Change(field, if (enabled) 2 else 1,
                "DMS_CAMERA_STATUS", if (enabled) 1 else 0, 0..1))
            val on = if (action in setOf("SET_DRIVER_GESTURES", "SET_LEAD_VEHICLE_DEPARTURE_ALERT", "SET_PEDESTRIAN_WARNING_SOUND")) 2 else 1
            return listOf(Change(field, if (enabled) on else 3 - on))
        }
    }
}
