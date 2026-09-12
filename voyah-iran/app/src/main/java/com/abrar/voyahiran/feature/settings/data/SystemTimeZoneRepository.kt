package com.abrar.voyahiran.feature.settings.data

import com.abrar.voyahiran.feature.settings.domain.*

interface SystemTimeZonePort {
    fun hasAccess(): Boolean
    fun read(): TimeZoneSnapshot
    fun setAutomatic(value: Boolean?)
    fun setZone(zone: String)
}

/** Uses the platform time service through the provisioned bridge, never an app-launched su. */
class SystemTimeZoneRepository(private val port: SystemTimeZonePort) : TimeZoneRepository {
    override fun read() = port.read()

    override fun setMode(mode: TimeZoneMode): TimeZoneSnapshot {
        if (!port.hasAccess()) throw TimeZoneException(TimeZoneFailure.ACCESS_REQUIRED)
        val previous = read()
        try {
            port.setAutomatic(false)
            port.setZone(mode.zoneId)
            return read().also {
                if (it.zoneId != mode.zoneId || it.automatic != false) {
                    throw TimeZoneException(TimeZoneFailure.VERIFICATION_FAILED)
                }
            }
        } catch (error: Exception) {
            // Restore both independent settings even if one rollback operation fails.
            runCatching { port.setZone(previous.zoneId) }.exceptionOrNull()?.let(error::addSuppressed)
            runCatching { port.setAutomatic(previous.automatic) }.exceptionOrNull()?.let(error::addSuppressed)
            if (error is TimeZoneException) throw error
            throw TimeZoneException(TimeZoneFailure.WRITE_FAILED, error.message.orEmpty()).also { it.initCause(error) }
        }
    }
}
