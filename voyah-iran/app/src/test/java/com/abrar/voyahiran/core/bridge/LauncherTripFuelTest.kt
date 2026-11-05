package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class LauncherTripFuelTest {
    @Test fun realZeroAndPositiveConsumptionArePreserved() {
        assertEquals(0.0,LauncherTripFuel.average(0,2,10)!!,0.0)
        assertEquals(6.2,LauncherTripFuel.average(6.2,2,10)!!,0.0)
    }
    @Test fun initializedElectricOnlyJourneyShowsZeroForEmptyOemAverage() {
        assertEquals(0.0,LauncherTripFuel.average(-9999,0.3,220)!!,0.0)
        assertEquals(0.0,LauncherTripFuel.average(-9999,0,0)!!,0.0)
    }
    @Test fun missingOrInvalidJourneyIsNotFabricatedAsZero() {
        assertNull(LauncherTripFuel.average(null,2,10))
        assertNull(LauncherTripFuel.average(-1000,2,10))
        assertNull(LauncherTripFuel.average(-9999,null,10))
        assertNull(LauncherTripFuel.average(-9999,2,-1000))
        assertNull(LauncherTripFuel.average(Double.NaN,2,10))
    }
}
