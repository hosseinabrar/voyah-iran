package com.abrar.voyahiran.core.bridge

/** Fixed local provisioning only; no arbitrary service name accepted from clients. */
internal object LauncherDrawerAccess {
    val arguments = listOf("/system/bin/sh", "-c", """
set -e
service=com.abrar.voyahiran/com.abrar.voyahiran.feature.launcher.LauncherDrawerService
current=${'$'}(settings --user 0 get secure enabled_accessibility_services)
[ "${'$'}current" != null ] || current=
case ":${'$'}current:" in
  *":${'$'}service:"*) ;;
  *) settings --user 0 put secure enabled_accessibility_services "${'$'}{current:+${'$'}current:}${'$'}service" ;;
esac
enabled=${'$'}(settings --user 0 get secure accessibility_enabled)
[ "${'$'}enabled" = 1 ] || settings --user 0 put secure accessibility_enabled 1
actual=${'$'}(settings --user 0 get secure enabled_accessibility_services)
case ":${'$'}actual:" in
  *":${'$'}service:"*) echo LAUNCHER_DRAWER_CONFIGURED ;;
  *) exit 1 ;;
esac
""".trimIndent())
}
