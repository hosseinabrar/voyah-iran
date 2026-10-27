package com.abrar.voyahiran.feature.voice

import com.abrar.raha.RahaAction
import org.junit.Assert.*
import org.junit.Test

class RahaVehiclePlanTest {
    @Test fun readyUsesRahaShadeOpenWithoutTouchingRoofGlass() {
        assertEquals(listOf(RahaWrite("vehicle", "ROLL_OPEN_PERCENT", 100)),
            RahaVehiclePlan(Snapshot()).create(RahaAction.SET_SUNSHADE_POSITION, mapOf("position" to 100)))
    }
    @Test fun departureUsesRahaShadeCloseWithoutTouchingRoofGlass() {
        assertEquals(listOf(RahaWrite("vehicle", "ROLL_OPEN_PERCENT", 0)),
            RahaVehiclePlan(Snapshot()).create(RahaAction.SET_SUNSHADE_POSITION, mapOf("position" to 0)))
    }
    private class Snapshot : RahaVehicleSnapshot {
        var left = 27.0
        var right = 20.0
        var fanFeedback = 3
        var fog = 0
        var oversea = false
        var velocity = 0
        override fun speed() = velocity
        val values = mutableMapOf<String, Int>()
        override fun vehicle(field: String) = values[field] ?: 2
        override fun air(field: String) = 1
        override fun airValue(getter: String) = if (getter == "getAirWindSpeed") fanFeedback.toDouble() else if (getter == "getAirLeftTemperature") left else if (getter == "getAirRightTemperature") right else 3.0
        override fun light(getter: String): Int { assertEquals("getFogRear", getter); return fog }
        override fun overseas() = oversea
    }
    private val snapshot = Snapshot()
    private fun plan(action:RahaAction, params:Map<String,Any>):List<RahaWrite> =
        RahaVehiclePlan(snapshot).create(action,params)
    @Test fun suspensionUsesOemDriveModeRoute() {
        assertEquals(listOf(RahaWrite("drive", "mode", 4)), plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "high")))
        assertEquals(listOf(RahaWrite("drive", "mode", 3)), plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "low")))
        for(mode in listOf(1,2,5,6)) {
            snapshot.values["DRIVING_MODE_SET"]=mode
            assertEquals(listOf(RahaWrite("drive","mode",mode)),plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "medium")))
        }
        for(mode in listOf(3,4,-1)) {
            snapshot.values["DRIVING_MODE_SET"]=mode
            assertEquals(listOf(RahaWrite("drive","mode",2)),plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "medium")))
        }
    }
    @Test fun highHeightRespectsOemSpeedLimit() {
        for(speed in listOf(-1,40,90)) {
            snapshot.velocity=speed
            assertThrows(IllegalArgumentException::class.java) { plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "high")) }
        }
        snapshot.velocity=39
        assertEquals(4,plan(RahaAction.SET_SUSPENSION_HEIGHT, mapOf("level" to "high")).single().value)
        assertThrows(IllegalArgumentException::class.java) {
            RahaVehiclePlan(snapshot).create(RahaAction.SET_SUSPENSION_HEIGHT,mapOf("level" to "lowest"))
        }
    }
    @Test fun coolingCapsOnlyWarmerZone() {
        val writes = plan(RahaAction.SET_CLIMATE_POWER, mapOf("enabled" to true,"zone" to "front","acEnabled" to true,"temperatureMaxC" to 23.0))
        assertTrue(writes.contains(RahaWrite("air-state", "AC_POWER_SWITCH", 1)))
        assertTrue(writes.contains(RahaWrite("air-state", "AC_SWITCH", 1)))
        assertEquals(listOf(RahaWrite("air", "AC_LEFT_TEMP", 230)), writes.filter { it.field.endsWith("TEMP") })
    }
    @Test fun heatingRaisesOnlyColderZone() {
        val writes = plan(RahaAction.SET_CLIMATE_POWER, mapOf("enabled" to true,"zone" to "front","acEnabled" to false,"temperatureMinC" to 26.0))
        assertEquals(listOf(RahaWrite("air", "AC_RIGHT_TEMP", 260)), writes.filter { it.field.endsWith("TEMP") })
    }
    @Test fun coolingStrengthReducesBothTemperatures() {
        val writes = plan(RahaAction.ADJUST_CABIN_TEMPERATURE, mapOf("delta" to -1.0,"zone" to "front"))
        assertEquals(listOf(260, 190), writes.map { it.value })
    }
    @Test fun driverSeatDoesNotTouchPassenger() {
        assertEquals(listOf(RahaWrite("vehicle", "FRONT_SEAT_VENTILATION_COMMAND_LEFT", 2)), plan(RahaAction.SET_SEAT_VENTILATION, mapOf("enabled" to true,"seat" to "driver")))
    }
    @Test fun fogAlreadyOnNeverTogglesOff() {
        snapshot.fog = 1
        assertTrue(plan(RahaAction.SET_REAR_FOG_LIGHT, mapOf("enabled" to true)).isEmpty())
        snapshot.fog = 0
        assertEquals(listOf(RahaWrite("vehicle", "REAR_FOG_LIGHT_SWITCH", 1)), plan(RahaAction.SET_REAR_FOG_LIGHT, mapOf("enabled" to true)))
    }
    @Test fun driveModesUseOemSnowAndPersonalValues() {
        assertEquals(6, plan(RahaAction.SET_DRIVE_MODE, mapOf("mode" to "snow")).single().value)
        assertEquals(5, plan(RahaAction.SET_DRIVE_MODE, mapOf("mode" to "personal")).single().value)
    }
    @Test fun fractureFreeRelativeShadeClampsAtLimit() {
        snapshot.values["ROLL_OPEN_PERCENT"] = 97
        val writes = RahaVehiclePlan(snapshot).create(RahaAction.ADJUST_SUNSHADE_POSITION, mapOf("deltaPercent" to 10))
        assertEquals(100, writes.single().value)
    }
    @Test fun windowHalfUsesPositionController() {
        assertEquals(listOf(RahaWrite("window-position", "DRIVER_WINDOW_CONTROL", 40)),
            plan(RahaAction.SET_WINDOW_POSITION, mapOf("window" to "driver","percent" to 50)))
    }
    @Test fun relativeWindowsUseTheirOwnCurrentPosition() {
        snapshot.values["BCM_FLWindowHorizontalSts"] = 20
        snapshot.values["BCM_FRWindowHorizontalSts"] = 95
        val result = RahaVehiclePlan(snapshot).create(RahaAction.ADJUST_WINDOW_POSITION,
            mapOf("window" to "front", "deltaPercent" to 10))
        assertEquals(listOf(25, 100), result.map { it.value })
    }
    @Test fun childLockDirectionsUseTheCombinedControl() {
        for (side in listOf("", "شاگرد", "راننده", "چپ", "راست")) {
            assertEquals(listOf(RahaWrite("vehicle", "CHILD_LOCK_CONTROL", 2)), plan(RahaAction.SET_CHILD_LOCK, mapOf("enabled" to true,"side" to "both")))
            assertEquals(listOf(RahaWrite("vehicle", "CHILD_LOCK_CONTROL", 1)), plan(RahaAction.SET_CHILD_LOCK, mapOf("enabled" to false,"side" to "both")))
        }
    }
    @Test fun massagePatternsUseTheOemControls() {
        assertEquals(listOf(RahaWrite("vehicle", "FRONT_SEAT_MASS_COMMAND_LEFT", 1)), plan(RahaAction.SET_SEAT_MASSAGE_PATTERN, mapOf("pattern" to "wave","seat" to "driver")))
        assertEquals(listOf(RahaWrite("vehicle", "FRONT_SEAT_MASS_COMMAND_LEFT", 2)), plan(RahaAction.SET_SEAT_MASSAGE_PATTERN, mapOf("pattern" to "rolling","seat" to "driver")))
        for (alias in listOf("چرخشی", "چرخش", "دایره ای", "غلتکی", "غلطکی")) {
            assertEquals(listOf(RahaWrite("vehicle", "FRONT_SEAT_MASS_COMMAND_LEFT", 2)), plan(RahaAction.SET_SEAT_MASSAGE_PATTERN, mapOf("pattern" to "rolling","seat" to "driver")))
        }
    }
    @Test fun repeatedButtonWorksWithoutDownEvents() {
        var time = 0L
        val policy = VoiceButtonPolicy { time }
        assertTrue(policy.trigger(0x2000))
        assertFalse(policy.trigger(0x2000))
        time = 500
        assertTrue(policy.trigger(0x2000))
        assertFalse(policy.trigger(0x10000))
    }
    @Test fun fourHeadlightModesAndOffFallbacks() {
        val cases = listOf(
            "چراغ اتوماتیک رو خاموش کن" to (2 to "POSITION_LAMP_SWITCH"),
            "نور پایین رو خاموش کن" to (2 to "POSITION_LAMP_SWITCH"),
            "دی لایت رو روشن کن" to (2 to "POSITION_LAMP_SWITCH"),
            "دی لایت رو خاموش کن" to (3 to "OUT_LAMP_OFF"),
            "چراغ ها رو خاموش کن" to (3 to "OUT_LAMP_OFF"),
            "چراغ اتوماتیک" to (0 to "AUTO_LAMP_SWITCH"),
            "نور پایین روشن کن" to (1 to "LOW_BEAM_SWITCH")
        )
        for ((spoken, expected) in cases) for (current in 0..3) {
            snapshot.values["LAMP_SWITCH_POSITION_FB"] = current
            assertEquals(spoken, if (current == expected.first) emptyList<RahaWrite>()
                else listOf(RahaWrite("vehicle", expected.second, 1)), plan(RahaAction.SET_HEADLIGHT_MODE,mapOf("mode" to listOf("auto","lowBeam","position","off")[expected.first])))
        }
    }
    @Test fun acUsesDesiredStateInsteadOfRawToggle() {
        assertEquals(listOf(RahaWrite("air-state", "AC_POWER_SWITCH", 1), RahaWrite("air-state", "AC_SWITCH", 1)), plan(RahaAction.SET_AC_POWER, mapOf("enabled" to true)))
        assertEquals(listOf(RahaWrite("air-state", "AC_SWITCH", 0)), plan(RahaAction.SET_AC_POWER, mapOf("enabled" to false)))
        assertTrue(plan(RahaAction.SET_CLIMATE_POWER, mapOf("enabled" to true,"zone" to "front","acEnabled" to true,"temperatureMaxC" to 23.0)).contains(RahaWrite("air-state", "AC_SWITCH", 1)))
        assertTrue(plan(RahaAction.SET_CLIMATE_POWER, mapOf("enabled" to true,"acEnabled" to false)).contains(RahaWrite("air-state", "AC_SWITCH", 0)))
    }
    @Test fun parkingBrakeMatchesOemEngageAndReleaseValues() {
        for ((engaged,value) in listOf(true to 1,false to 2))
            assertEquals(listOf(RahaWrite("vehicle","EPB_PARK_SWITCH",value)),
                RahaVehiclePlan(snapshot).create(RahaAction.SET_PARKING_BRAKE,mapOf("engaged" to engaged)))
    }
    @Test fun climatePulseAndDirectCommandsRemainDistinct() {
        val planner=RahaVehiclePlan(snapshot)
        for ((action,field) in listOf(RahaAction.SET_CLIMATE_POWER to "AC_POWER_SWITCH",
            RahaAction.SET_CLIMATE_AUTO to "AC_AUTO",RahaAction.SET_FRONT_DEFROSTER to "AC_FRONT_DEFROST_SWITCH")) {
            for(on in listOf(false,true)) {
                val writes=planner.create(action,mapOf("enabled" to on))
                assertTrue(writes.contains(RahaWrite("air-state",field,if(on)1 else 0)))
            }
        }
        for(on in listOf(false,true)) {
            assertEquals(listOf(RahaWrite("air","AC_REAR_DEFROST_SWITCH",if(on)1 else 0)),
                planner.create(RahaAction.SET_REAR_DEFROSTER,mapOf("enabled" to on)))
            assertEquals(listOf(RahaWrite("air-state","AC_DUAL",if(on)0 else 1)),
                planner.create(RahaAction.SET_FRONT_CLIMATE_SYNC,mapOf("enabled" to on)))
        }
        for((name,value) in listOf("internal" to 0,"external" to 1,"auto" to 2))
            assertEquals(listOf(RahaWrite("air-state","AC_RECIRC_AIR",value)),
                planner.create(RahaAction.SET_AIR_RECIRCULATION,mapOf("mode" to name)))
    }
    @Test fun maximumCoolingUsesSupportedCommands() {
        val writes=RahaVehiclePlan(snapshot).create(RahaAction.SET_MAX_COOLING,mapOf("enabled" to true))
        assertFalse(writes.any { it.field == "AC_MAX_SWITCH" })
        assertTrue(writes.contains(RahaWrite("air","AC_LEFT_TEMP",155)))
        assertTrue(writes.contains(RahaWrite("air","AC_RIGHT_TEMP",155)))
        assertTrue(writes.contains(RahaWrite("air","AC_BLOWER",8)))
        assertTrue(writes.contains(RahaWrite("air-state","AC_SWITCH",1)))
        assertTrue(writes.contains(RahaWrite("air-state","AC_RECIRC_AIR",0)))
        try { RahaVehiclePlan(snapshot).create(RahaAction.SET_MAX_COOLING,mapOf("enabled" to false));fail("no native max-off") }
        catch(_:IllegalArgumentException) {}
    }
    @Test fun autoHoldSelectsModesInsteadOfPower() {
        assertEquals(1, plan(RahaAction.SET_AUTO_HOLD, mapOf("mode" to "automatic")).single().value)
        assertEquals(2, plan(RahaAction.SET_AUTO_HOLD, mapOf("mode" to "deepPedal")).single().value)
    }
    @Test fun espPhysicalMeaningDoesNotDependOnExportUi() {
        for (overseas in listOf(false,true)) {
            snapshot.oversea=overseas
            assertEquals(2,plan(RahaAction.SET_STABILITY_CONTROL, mapOf("enabled" to true)).single().value)
            assertEquals(1,plan(RahaAction.SET_STABILITY_CONTROL, mapOf("enabled" to false)).single().value)
        }
    }
    @Test fun ambientRelativeCommandsMoveTwoLevelsAndClamp() {
        val planner=RahaVehiclePlan(snapshot)
        for(current in 1..10) for(delta in listOf(-1,1)) {
            snapshot.values["VEHICLE_AMBIENT_LIGHT_BRIGHTNESS_LEVEL"]=current
            assertEquals((current+delta*2).coerceIn(1,10),
                planner.create(RahaAction.ADJUST_AMBIENT_LIGHT_BRIGHTNESS,mapOf("delta" to delta)).single().value)
        }
    }
    @Test fun fanRelativeCommandsConvertFeedbackIndexToRequestLevel() {
        val planner=RahaVehiclePlan(snapshot)
        for(raw in 0..7) for(delta in listOf(-1,1)) {
            snapshot.fanFeedback=raw
            val writes=planner.create(RahaAction.ADJUST_FAN_SPEED,mapOf("zone" to "front","delta" to delta))
            assertEquals((raw+1+delta).coerceIn(1,8),writes.single { it.field=="AC_BLOWER" }.value)
        }
        snapshot.fanFeedback=4
        assertEquals(6,plan(RahaAction.ADJUST_FAN_SPEED, mapOf("delta" to 1,"zone" to "front")).last().value)
        assertEquals(4,plan(RahaAction.ADJUST_FAN_SPEED, mapOf("delta" to -1,"zone" to "front")).last().value)
        assertEquals(6,plan(RahaAction.SET_FAN_SPEED, mapOf("level" to 5,"zone" to "front")).last().value)
    }
    @Test fun smallWindowStepsAreHalvedInBothDirections() {
        for(current in listOf(0,3,50,97,100)) for(delta in listOf(-10,10)) {
            snapshot.values["BCM_FLWindowHorizontalSts"]=current
            val result=RahaVehiclePlan(snapshot).create(RahaAction.ADJUST_WINDOW_POSITION,
                mapOf("window" to "driver","deltaPercent" to delta))
            assertEquals((current+delta/2).coerceIn(0,100),result.single().value)
        }
        assertEquals(40,plan(RahaAction.SET_WINDOW_POSITION, mapOf("window" to "driver","percent" to 50)).single().value)
        assertEquals(50,RahaVehiclePlan(snapshot).create(RahaAction.SET_SUNSHADE_POSITION,mapOf("position" to 50)).single().value)
    }
    @Test fun sunroofSmallRequestsMoveTwentyAndVentUsesNativeAllWindows() {
        snapshot.values["SUNROOF_OPEN_PERCENT"]=50
        assertEquals(70,plan(RahaAction.ADJUST_SUNROOF_POSITION, mapOf("deltaPercent" to 10)).single().value)
        assertEquals(30,plan(RahaAction.ADJUST_SUNROOF_POSITION, mapOf("deltaPercent" to -10)).single().value)
        snapshot.values["SUNROOF_OPEN_PERCENT"]=95
        assertEquals(100,plan(RahaAction.ADJUST_SUNROOF_POSITION, mapOf("deltaPercent" to 10)).single().value)
        snapshot.values["SUNROOF_OPEN_PERCENT"]=5
        assertEquals(0,plan(RahaAction.ADJUST_SUNROOF_POSITION, mapOf("deltaPercent" to -10)).single().value)
        for(noun in listOf("شیشه ها","همه شیشه ها","پنجره ها"))
        for(request in listOf("رو ببر تو حالت تهویه","رو بذار روی حالت تهویه","رو تو وضعیت تهویه قرار بده",
            "حالت تهویه رو فعال کن","رو ونت کن","را در حالت تهویه قرار دهید")) {
            assertEquals("$noun $request",listOf(RahaWrite("vehicle","ALL_WINDOW_CONTROL",6)),plan(RahaAction.VENT_WINDOW, mapOf("window" to "all")))
        }
    }
    @Test fun directFanLevelUsesSameOffsetAsFeedback() {
        val planner=RahaVehiclePlan(snapshot)
        assertEquals(3,plan(RahaAction.SET_FAN_SPEED, mapOf("level" to 2,"zone" to "front")).last().value)
        for(level in 1..8) {
            val writes=planner.create(RahaAction.SET_FAN_SPEED,mapOf("zone" to "front","level" to level))
            assertEquals((level+1).coerceAtMost(8),writes.single { it.field=="AC_BLOWER" }.value)
        }
        assertEquals(8,planner.create(RahaAction.SET_FAN_SPEED,mapOf("level" to "max")).last().value)
        assertEquals(2,planner.create(RahaAction.SET_FAN_SPEED,mapOf("level" to "min")).last().value)
    }
}
