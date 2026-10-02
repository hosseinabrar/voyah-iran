package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class EnergyStrategyPolicyTest {
    @org.junit.Test fun h97aLegacyMenuUsesFixedLabels() {
        org.junit.Assert.assertEquals(mapOf("smart" to 1,"electric" to 2,"fuel" to 3,"save" to 4),EnergyStrategyPolicy.legacyModes(true))
        org.junit.Assert.assertTrue(EnergyStrategyPolicy.legacyModes(false).isEmpty())
        org.junit.Assert.assertFalse(EnergyStrategyPolicy.legacyModes(true).containsKey("hybrid"))
    }

    @Test fun fuelFirstVehiclesNeverAcceptSaveOrSmart() {
        for (ca in listOf(false, true)) {
            val modes = EnergyStrategyPolicy.modes(true, ca, true)
            assertEquals(4, modes["fuel"])
            assertEquals(3, modes["hybrid"])
            assertEquals(2, modes["electric"])
            assertNull(modes["save"])
            assertNull(modes["smart"])
            assertNull(modes["forcedElectric"])
        }
    }
    @Test fun chinaMenuUsesItsOwnLabels() {
        val legacy = EnergyStrategyPolicy.modes(true, false, false)
        assertEquals(1, legacy["smart"])
        assertEquals(3, legacy["fuel"])
        assertEquals(4, legacy["save"])
        assertNull(legacy["hybrid"])
        val ca = EnergyStrategyPolicy.modes(true, true, false)
        assertEquals(3, ca["hybrid"])
        assertEquals(4, ca["save"])
        assertNull(ca["fuel"])
        assertNull(ca["smart"])
    }
    @Test fun nonExtenderVehicleHasNoStrategies() {
        for (ca in listOf(false, true)) for (x in listOf(false, true))
            assertTrue(EnergyStrategyPolicy.modes(false, ca, x).isEmpty())
    }
    @Test fun reserveUsesMenuEncoding() {
        assertEquals(0, EnergyStrategyPolicy.reserve(25))
        assertEquals(5, EnergyStrategyPolicy.reserve(50))
        assertEquals(11, EnergyStrategyPolicy.reserve(80))
        for (n in listOf(-1, 0, 24, 26, 81, 100))
            assertThrows(IllegalArgumentException::class.java) { EnergyStrategyPolicy.reserve(n) }
    }
}
