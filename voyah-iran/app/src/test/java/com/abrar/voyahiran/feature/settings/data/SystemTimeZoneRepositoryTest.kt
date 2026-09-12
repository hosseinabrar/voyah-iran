package com.abrar.voyahiran.feature.settings.data

import com.abrar.voyahiran.feature.settings.domain.*
import org.junit.Assert.*
import org.junit.Test

class SystemTimeZoneRepositoryTest {
    private class Port : SystemTimeZonePort {
        var allowed = true
        var value = TimeZoneSnapshot("Asia/Tehran", null)
        var reject = false
        var ignore = false
        val writes = mutableListOf<String>()
        override fun hasAccess() = allowed
        override fun read() = value
        override fun setAutomatic(value: Boolean?) {
            writes += "auto:$value"
            this.value = this.value.copy(automatic = value)
        }
        override fun setZone(zone: String) {
            writes += "zone:$zone"
            if (reject && zone == "GMT+03:30") error("denied")
            if (!ignore) value = value.copy(zoneId = zone)
        }
    }
    @Test fun missingPermissionDoesNotMutateSettings() {
        val p = Port().apply { allowed = false }
        try { SystemTimeZoneRepository(p).setMode(TimeZoneMode.FIXED_IRAN); fail() }
        catch (e: TimeZoneException) { assertEquals(TimeZoneFailure.ACCESS_REQUIRED, e.failure) }
        assertTrue(p.writes.isEmpty())
    }
    @Test fun setsFixedOffsetAndDisablesAutomaticZone() {
        val p = Port()
        assertEquals(TimeZoneSnapshot("GMT+03:30", false), SystemTimeZoneRepository(p).setMode(TimeZoneMode.FIXED_IRAN))
        assertEquals(listOf("auto:false", "zone:GMT+03:30"), p.writes)
    }
    @Test fun disablingCorrectionSelectsTehran() {
        val p = Port().apply { value = TimeZoneSnapshot("GMT+03:30", false) }
        assertEquals("Asia/Tehran", SystemTimeZoneRepository(p).setMode(TimeZoneMode.TEHRAN).zoneId)
    }
    @Test fun failedWriteRestoresAbsentAutomaticSetting() {
        val p = Port().apply { reject = true }
        try { SystemTimeZoneRepository(p).setMode(TimeZoneMode.FIXED_IRAN); fail() }
        catch (e: TimeZoneException) { assertEquals(TimeZoneFailure.WRITE_FAILED, e.failure) }
        assertEquals(TimeZoneSnapshot("Asia/Tehran", null), p.value)
        assertEquals("auto:null", p.writes.last())
    }
    @Test fun successfulReturnWithoutActualChangeFailsVerification() {
        val p = Port().apply { ignore = true; value = value.copy(automatic = true) }
        try { SystemTimeZoneRepository(p).setMode(TimeZoneMode.FIXED_IRAN); fail() }
        catch (e: TimeZoneException) { assertEquals(TimeZoneFailure.VERIFICATION_FAILED, e.failure) }
        assertEquals(true, p.value.automatic)
    }
}
