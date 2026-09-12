package com.abrar.voyahiran.feature.settings.data

import android.content.Context
import android.provider.Settings
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.feature.settings.domain.*

class AndroidSystemTimeZonePort(context: Context, private val runner: CommandRunner) : SystemTimeZonePort {
    private val context = context.applicationContext
    private val reader = RootTimeZoneRepository(runner) // read() is unprivileged; no root invocation.
    override fun hasAccess(): Boolean {
        val result = runner.run(listOf("su", "0", "id", "-u"), 5)
        val ready = !result.timedOut && result.exitCode == 0 && result.output.trim() == "0"
        AppLog.info("TimeZone", "backend=authenticated-bridge ready=$ready")
        return ready
    }

    override fun read(): TimeZoneSnapshot = reader.read().copy(automatic =
        when (Settings.Global.getString(context.contentResolver, Settings.Global.AUTO_TIME_ZONE)) {
            "0" -> false
            "1" -> true
            else -> null
        })

    override fun setAutomatic(value: Boolean?) {
        val args = if (value == null) listOf("settings", "delete", "global", "auto_time_zone")
            else listOf("settings", "put", "global", "auto_time_zone", if (value) "1" else "0")
        val result = runner.run(listOf("su", "0") + args, 8)
        check(result.exitCode == 0 && !result.timedOut && !result.output.contains("Exception", true)) { "Cannot update automatic timezone" }
    }

    override fun setZone(zone: String) {
        // Android's public wrapper rejects fixed GMT offsets. The supplied firmware's
        // IAlarmManager transaction 3 accepts a String; the service still enforces
        // SET_TIME_ZONE on the bridge's root UID. The APK remains an ordinary app.
        // Also supports restoring the prior system value after a failed operation.
        val result = runner.run(listOf("su", "0", "service", "call", "alarm", "3", "s16", zone), 8)
        if (result.timedOut) throw TimeZoneException(TimeZoneFailure.TIMEOUT)
        check(result.exitCode == 0 && Regex("Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000\\b")
            .containsMatchIn(result.output)) { "Alarm service rejected timezone request" }
    }
}
