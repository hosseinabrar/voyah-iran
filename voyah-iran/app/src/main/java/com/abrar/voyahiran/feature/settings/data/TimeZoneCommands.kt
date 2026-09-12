package com.abrar.voyahiran.feature.settings.data

import com.abrar.voyahiran.feature.settings.domain.TimeZoneMode

/** Only enum literals can enter privileged commands. Never accepts user-provided shell text. */
internal object TimeZoneCommands {
    const val READ = """
        zone=${'$'}(/system/bin/getprop persist.sys.timezone) || exit 40
        [ -n "${'$'}zone" ] || exit 40
        printf 'ZONE=%s\n' "${'$'}zone"
        auto=${'$'}(/system/bin/settings get global auto_time_zone 2>/dev/null)
        printf 'AUTO=%s\n' "${'$'}auto"
    """

    fun apply(mode: TimeZoneMode): String = """
        [ "${'$'}(/system/bin/id -u)" = "0" ] || exit 41
        old_zone=${'$'}(/system/bin/getprop persist.sys.timezone) || exit 40
        [ -n "${'$'}old_zone" ] || exit 40
        old_auto=${'$'}(/system/bin/settings get global auto_time_zone) || exit 40
        case "${'$'}old_auto" in 0|1|null) ;; *) exit 40 ;; esac
        rollback() {
            /system/bin/setprop persist.sys.timezone "${'$'}old_zone"
            if [ "${'$'}old_auto" = "null" ]; then
                /system/bin/settings delete global auto_time_zone
            else
                /system/bin/settings put global auto_time_zone "${'$'}old_auto"
            fi
            /system/bin/am broadcast --user all -a android.intent.action.TIMEZONE_CHANGED --es time-zone "${'$'}old_zone" >/dev/null 2>&1
        }
        trap 'rollback; exit 44' HUP INT TERM
        /system/bin/settings put global auto_time_zone 0 || { rollback; exit 42; }
        /system/bin/setprop persist.sys.timezone '${mode.zoneId}' || { rollback; exit 42; }
        [ "${'$'}(/system/bin/getprop persist.sys.timezone)" = '${mode.zoneId}' ] || { rollback; exit 43; }
        [ "${'$'}(/system/bin/settings get global auto_time_zone)" = "0" ] || { rollback; exit 43; }
        /system/bin/am broadcast --user all -a android.intent.action.TIMEZONE_CHANGED --es time-zone '${mode.zoneId}' || { rollback; exit 42; }
        printf '\nVOYAH_TIMEZONE_OK\n'
    """.trimIndent()
}
