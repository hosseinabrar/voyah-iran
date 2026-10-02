package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class DriverAssistanceExecutorTest {
    @org.junit.Test fun h97aLaneModeUsesSingleStateAndPreservesExistingMode() {
        org.junit.Assert.assertEquals(2, DriverAssistanceExecutor.legacyLaneMode(mapOf("enabled" to true),1))
        for(current in 2..4) org.junit.Assert.assertEquals(current, DriverAssistanceExecutor.legacyLaneMode(mapOf("enabled" to true),current))
        org.junit.Assert.assertEquals(1, DriverAssistanceExecutor.legacyLaneMode(mapOf("enabled" to false),4))
        org.junit.Assert.assertEquals(3, DriverAssistanceExecutor.legacyLaneMode(mapOf("mode" to "keeping"),2))
    }

    @Test fun legacyLaneWritesModeInsteadOfModernMasterSwitch() {
        val fake=Fake().apply {values["LDW_RDP_LKS_FUNC_ENABLE"]=1}
        val executor=DriverAssistanceExecutor(fake,legacyLane=true)
        executor.execute("SET_LANE_SUPPORT_MODE",mapOf("enabled" to true))
        assertEquals(listOf("LDW_RDP_LKS_FUNC_ENABLE" to 2),fake.writes)
        fake.values["LDW_RDP_LKS_FUNC_ENABLE"]=4; fake.writes.clear()
        executor.execute("SET_LANE_SUPPORT_MODE",mapOf("enabled" to true))
        assertTrue(fake.writes.isEmpty())
        executor.execute("SET_LANE_SUPPORT_MODE",mapOf("enabled" to false))
        assertEquals(listOf("LDW_RDP_LKS_FUNC_ENABLE" to 1),fake.writes)
    }
    @Test fun legacyWarningTypeIsRejectedBeforeWriting() {
        val fake=Fake().apply {values["LDW_WARNING_TYPE"]=1}
        var rejected=false
        try {DriverAssistanceExecutor(fake,legacyLane=true).execute("SET_LANE_DEPARTURE_ALERT_TYPE",mapOf("type" to "vibration"))}
        catch(_:IllegalStateException) {rejected=true}
        assertTrue(rejected); assertTrue(fake.writes.isEmpty())
    }

    private class Fake : DriverAssistancePort {
        val values = mutableMapOf<String, Int>()
        val writes = mutableListOf<Pair<String, Int>>()
        val confirmations = mutableListOf<Pair<String, Int>>()
        var reject = false
        override fun validate(field: String) { check(values.containsKey(field)) }
        override fun read(field: String) = values.getValue(field)
        override fun write(field: String, value: Int) {
            writes += field to value
            if (!reject) {
                if (field == "DMS_CAMERA_CTL") values["DMS_CAMERA_STATUS"] = value - 1
                else values[field] = value
            }
        }
        override fun confirm(field: String, value: Int) {
            confirmations += field to value
            check(read(field) == value)
        }
        override fun pause(milliseconds: Long) {}
    }

    @Test fun factoryTogglePolaritiesMatchBindings() {
        val standard = mapOf("SET_DOOR_OPEN_WARNING" to "DOW_SYSTEM_SWITCH",
            "SET_FORWARD_COLLISION_WARNING" to "FCW_SWITCH", "SET_REAR_CROSS_TRAFFIC_WARNING" to "RCTA_SWITCH",
            "SET_REAR_COLLISION_WARNING" to "RCW_SWITCH", "SET_AUTOMATIC_EMERGENCY_BRAKING" to "AEB_SWITCH",
            "SET_BLIND_SPOT_MONITORING" to "BSD_SYSTEM_SWITCH", "SET_FRONT_CROSS_TRAFFIC_WARNING" to "FCTA_SWITCH",
            "SET_TRAFFIC_SIGN_RECOGNITION" to "TSR_SWITCH")
        val reversed = mapOf("SET_DRIVER_GESTURES" to "GESTUREID_REQ", "SET_LEAD_VEHICLE_DEPARTURE_ALERT" to "FVSR_SWITCH")
        (standard + reversed).forEach { (action, field) ->
            val on = if (action in reversed) 2 else 1
            val port = Fake().apply { values[field] = 3 - on }
            val executor = DriverAssistanceExecutor(port)
            executor.execute(action, mapOf("enabled" to true))
            executor.execute(action, mapOf("enabled" to false))
            assertEquals(listOf(field to on, field to 3 - on), port.writes)
            assertEquals(port.writes, port.confirmations)
        }
    }

    @Test fun cameraUsesSeparateZeroBasedFeedback() {
        val p = Fake().apply { values["DMS_CAMERA_CTL"] = 1; values["DMS_CAMERA_STATUS"] = 0 }
        val executor = DriverAssistanceExecutor(p)
        executor.execute("SET_DRIVER_MONITORING", mapOf("enabled" to true))
        executor.execute("SET_DRIVER_MONITORING", mapOf("enabled" to false))
        assertEquals(listOf("DMS_CAMERA_CTL" to 2, "DMS_CAMERA_CTL" to 1), p.writes)
        assertEquals(listOf("DMS_CAMERA_STATUS" to 1, "DMS_CAMERA_STATUS" to 0), p.confirmations)
    }

    @Test fun failedAcknowledgementDoesNotProceedToSecondWrite() {
        val p = Fake().apply { values["LANE_ASSIST_SWITCH"] = 2; values["LDW_RDP_LKS_FUNC_ENABLE"] = 2; reject = true }
        assertThrows(IllegalStateException::class.java) {
            DriverAssistanceExecutor(p).execute("SET_LANE_SUPPORT_MODE", mapOf("mode" to "keeping"))
        }
        assertEquals(listOf("LANE_ASSIST_SWITCH" to 1), p.writes)
    }

    @Test fun missingOrInvalidSecondaryStatePreventsAllWrites() {
        listOf<Int?>(null, 255).forEach { secondary ->
            val p = Fake().apply { values["LANE_ASSIST_SWITCH"] = 2; secondary?.let { values["LDW_RDP_LKS_FUNC_ENABLE"] = it } }
            assertThrows(IllegalStateException::class.java) {
                DriverAssistanceExecutor(p).execute("SET_LANE_SUPPORT_MODE", mapOf("mode" to "keeping"))
            }
            assertTrue(p.writes.isEmpty())
        }
    }

    @Test fun alreadyAppliedIsIdempotentAndCenteringIsNotGuessed() {
        val p = Fake().apply { values["DOW_SYSTEM_SWITCH"] = 1 }
        DriverAssistanceExecutor(p).execute("SET_DOOR_OPEN_WARNING", mapOf("enabled" to true))
        assertTrue(p.writes.isEmpty())
        assertThrows(IllegalStateException::class.java) { DriverAssistanceExecutor.plan("SET_LANE_SUPPORT_MODE", mapOf("mode" to "centering")) }
    }

    @Test fun sensitivityAndLaneCommandsUseExactOemValues() {
        assertEquals(listOf(DriverAssistanceExecutor.Change("LANE_ASSIST_SWITCH", 1)), DriverAssistanceExecutor.plan("SET_LANE_SUPPORT_MODE", mapOf("enabled" to true)))
        assertEquals(listOf(DriverAssistanceExecutor.Change("LDW_LKS_SENSITIVITY", 1)), DriverAssistanceExecutor.plan("SET_LANE_SUPPORT_MODE", mapOf("sensitivity" to "min")))
        assertEquals(listOf(DriverAssistanceExecutor.Change("LDW_LKS_SENSITIVITY", 2)), DriverAssistanceExecutor.plan("SET_LANE_SUPPORT_MODE", mapOf("sensitivity" to "max")))
        fun plan(action: String, key: String, value: String) = DriverAssistanceExecutor.plan(action, mapOf(key to value)).map { it.field to it.value }
        assertEquals(listOf("FCW_SENSITIVITY" to 3), plan("SET_FORWARD_COLLISION_WARNING", "sensitivity", "max"))
        assertEquals(listOf("DMS_WEARY_SET" to 2, "DMS_SEN_SET" to 0), plan("SET_DRIVER_MONITORING", "sensitivity", "min"))
        assertEquals(listOf("DMS_WEARY_SET" to 2, "DMS_SEN_SET" to 1), plan("SET_DRIVER_MONITORING", "sensitivity", "max"))
        assertEquals(listOf("LANE_ASSIST_SWITCH" to 1, "LDW_RDP_LKS_FUNC_ENABLE" to 2), plan("SET_LANE_SUPPORT_MODE", "mode", "warning"))
        assertEquals(listOf("LANE_ASSIST_SWITCH" to 1, "LDW_RDP_LKS_FUNC_ENABLE" to 3), plan("SET_LANE_SUPPORT_MODE", "mode", "keeping"))
        assertEquals(listOf("LANE_ASSIST_SWITCH" to 2), plan("SET_LANE_SUPPORT_MODE", "mode", "off"))
        assertEquals(listOf("LDW_WARNING_TYPE" to 2), plan("SET_LANE_DEPARTURE_ALERT_TYPE", "type", "vibration"))
    }
}
