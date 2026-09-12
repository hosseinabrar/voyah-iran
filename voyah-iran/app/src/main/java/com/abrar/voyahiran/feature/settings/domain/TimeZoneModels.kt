package com.abrar.voyahiran.feature.settings.domain

enum class TimeZoneMode(val zoneId: String) {
    FIXED_IRAN("GMT+03:30"),
    TEHRAN("Asia/Tehran");

    companion object {
        fun forCorrection(enabled: Boolean) = if (enabled) FIXED_IRAN else TEHRAN
    }
}

data class TimeZoneSnapshot(val zoneId: String, val automatic: Boolean?) {
    val correctionEnabled: Boolean get() = zoneId == TimeZoneMode.FIXED_IRAN.zoneId
}

enum class TimeZoneFailure { ROOT_UNAVAILABLE, ACCESS_REQUIRED, TIMEOUT, READ_FAILED, WRITE_FAILED, VERIFICATION_FAILED }

class TimeZoneException(val failure: TimeZoneFailure, detail: String = "") : Exception(detail)

interface TimeZoneRepository {
    /** Reads actual device state; a local preference never stands in for system state. */
    fun read(): TimeZoneSnapshot
    fun setMode(mode: TimeZoneMode): TimeZoneSnapshot
}
