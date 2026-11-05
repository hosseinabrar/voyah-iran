package com.abrar.voyahiran.core.bridge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LauncherTelemetryReadinessTest {
    @Test fun invalidEngineSnapshotKeepsRecoveryEnabledButOffIsValid() {
        for(rpm in listOf(-1000,-9999,65535))assertFalse(LauncherTelemetryReadiness.ready("getEngineSpeed",JSONObject().put("rpm",rpm)))
        assertTrue(LauncherTelemetryReadiness.ready("getEngineSpeed",JSONObject().put("rpm",0)))
        assertTrue(LauncherTelemetryReadiness.ready("getEngineSpeed",JSONObject().put("rpm",1850)))
    }
    @Test fun partialTripParcelDoesNotStopRecovery() {
        val trip=JSONObject("""{"distance":-1000,"minutes":0,"averageFuel":-9999,"averagePower":-1000}""")
        assertFalse(LauncherTelemetryReadiness.ready("getEnergyConsumptionInfo",trip))
        trip.put("distance",0).put("averageFuel",0)
        assertFalse(LauncherTelemetryReadiness.ready("getEnergyConsumptionInfo",trip))
        trip.put("averagePower",0)
        assertTrue(LauncherTelemetryReadiness.ready("getEnergyConsumptionInfo",trip))
    }
    @Test fun fuelLevelAloneDoesNotStopInstantConsumptionRecovery() {
        val fuel=JSONObject().put("fuel",50)
        assertFalse(LauncherTelemetryReadiness.ready("getFuelLevel",fuel))
        fuel.put("instantFuel",0)
        assertTrue(LauncherTelemetryReadiness.ready("getFuelLevel",fuel))
    }
}
