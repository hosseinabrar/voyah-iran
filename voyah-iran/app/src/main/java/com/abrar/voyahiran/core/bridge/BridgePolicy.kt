package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter

/** The wire protocol is not a shell: accept only the app's existing, bounded operations. */
object BridgePolicy {
    const val PORT = 38743
    const val MAGIC = 0x56495231
    val openApp = listOf("am", "start", "--user", "0", "--activity-single-top", "--activity-clear-top",
        "-n", "com.abrar.voyahiran/.ui.MainActivity")

    val openCamera = listOf("roa-camera", "open")
    private val cameraLaunch = listOf("am", "start", "--user", "0", "--display", "0", "-n",
        "com.qinggan.camera/.activity.RecordVideoActivity", "--ei", "mInType", "0",
        "--ei", "mPerceptionTakePic", "4", "--ez", "ispic", "false", "--es", "cameratype", "FRONT")
    fun resolve(request: List<String>, sdkInt: Int = 30): List<String>? {
        if(request == listOf("launcher-vehicle","read")) return request
        if(request.size==3 && request[0]=="launcher-vehicle" && when(request[1]) {
            "energy" -> request[2] in listOf("smart","electric","fuel","hybrid","save")
            "recovery" -> request[2] in listOf("2","3","4")
            "height" -> request[2] in listOf("0","1","2","3")
            "drive" -> request[2].toIntOrNull() in 1..6
            else -> false
        }) return request
        if (request.size == 3 && request[0] in setOf("launcher-map", "launcher-map-claim", "launcher-map-status") && AppDensityPreferences.validPackage(request[1]) && request[1] != "com.abrar.voyahiran" && request[2].toIntOrNull()?.let { it == 0 || it in 2..10000 } == true) return request
        if (request.size == 2 && request[0] == "launcher-home" && request[1] in listOf("enable", "disable"))
            return request
        if (request.size == 3 && request[0] == "app-density-package" && AppDensityPreferences.validPackage(request[1]) &&
            (request[2] == "inherit" || request[2] in listOf("120", "140", "160", "180", "200"))) return request
        if (request.size == 2 && request[0] == "app-density" && request[1] in listOf("120", "140", "160", "180", "200")) return request
        if (request.size == 2 && request[0] == "app-theme" && request[1] in listOf("dark", "light", "vehicle")) return request
        if (request == listOf("raha-phone", "device")) return request
        if (request.size == 4 && request[0] == "raha-phone" && request[2].matches(Regex("[0-9A-F]{2}(?::[0-9A-F]{2}){5}")) &&
            ((request[1] == "read" && request[3].toLongOrNull()?.let { it >= 0 } == true) ||
             (request[1] == "dial" && request[3].matches(Regex("\\+?[0-9]{3,20}"))))) return request
        if (request.size == 2 && request[0] == "local-profile-test" && request[1] in listOf("status", "apply", "restore")) return request
        if (request == listOf("apollo-capability")) return request
        if (request.size == 2 && request[0] == "avm-turn" && request[1] in listOf("read", "on", "off")) return request
        if (request == listOf("apollo-test-read")) return request
        if (request.size == 3 && request[0] == "apollo-test-set" &&
            com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.entries.any {
                it.name == request[1] && request[2].toIntOrNull()?.let(it::accepts) == true
            }) return request
        if (request.size == 3 && request[0] == "apollo-feature" &&
            com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.entries.any { it.name == request[1] } && request[2] in listOf("on", "off")) return request
        if (request.size == 2 && request[0] in listOf("apollo-state", "apollo-reconcile") && request[1] in listOf("1", "2")) return request
        if (request.size == 2 && request[0] == "system-logging" && request[1] in listOf("enable", "disable", "status")) return request
        if (request == listOf("steering-back", "center")) return request
        if (request.size == 2 && request[0] == "neshan-direct" && request[1] in listOf("enable", "disable", "status")) return request
        if (request == listOf("cluster-navigation", "clear")) return request
        if (request.size == 5 && request[0] == "cluster-navigation" && request[1] == "show" &&
            request[2].isNotBlank() && request[2].toByteArray(Charsets.UTF_8).size <= 180 &&
            request[2].none { it.isISOControl() } && request[3].toIntOrNull() in 0..8 &&
            request[4].toIntOrNull() in -1..1_000_000) return request
        if (request.size == 2 && request[0] == "cluster-navigation-access" && request[1] in listOf("enable", "disable"))
            return listOf("/system/bin/cmd", "notification", if (request[1] == "enable") "allow_listener" else "disallow_listener",
                "com.abrar.voyahiran/com.abrar.voyahiran.feature.navigation.NeshanClusterListener", "0")
        if (request == listOf("neshan-access", "enable")) return listOf("/system/bin/sh", "-c", """
set -e
service=com.abrar.voyahiran/com.abrar.voyahiran.feature.navigation.NeshanDrivingService
current=${'$'}(settings --user 0 get secure enabled_accessibility_services)
[ "${'$'}current" != null ] || current=
case ":${'$'}current:" in
  *":${'$'}service:"*) ;;
  *) settings --user 0 put secure enabled_accessibility_services "${'$'}{current:+${'$'}current:}${'$'}service" ;;
esac
settings --user 0 put secure accessibility_enabled 1
actual=${'$'}(settings --user 0 get secure enabled_accessibility_services)
case ":${'$'}actual:" in
  *":${'$'}service:"*) echo NESHAN_ACCESS_CONFIGURED ;;
  *) exit 1 ;;
esac
""".trimIndent())
        if (request.size == 2 && request[0] == "media-access" && request[1] in listOf("enable", "disable"))
            return listOf("/system/bin/cmd", "notification", if (request[1] == "enable") "allow_listener" else "disallow_listener",
                "com.abrar.voyahiran/com.abrar.voyahiran.feature.media.SteeringMediaListener", "0")
        if (request.size == 2 && request[0] == "passenger-apps" && request[1] in listOf("enable", "disable"))
            return listOf("/system/bin/settings", "put", "global", "voyah_iran_passenger_apps", if (request[1] == "enable") "1" else "0")
        if (request.size == 2 && request[0] == "screen-swap-runtime" && request[1] in listOf("prepare", "execute")) return request
        if (request.size == 2 && request[0] == "screen-swap" && request[1] in listOf("enable", "disable"))
            return listOf("/system/bin/settings", "put", "global", "voyah_iran_screen_swap", if (request[1] == "enable") "1" else "0")
        if (request.size == 2 && request[0] == "app-display" && request[1] in listOf("enable", "disable")) return request
        if(request==listOf("subscription-vin","read")) return request
        if(request.size==2 && request[0]=="subscription-session" && request[1].toIntOrNull() in 0..90) return request
        if(request==listOf("subscription-ui","open")) return listOf("/system/bin/am","start","--user","0","--display","0","-n","com.abrar.voyahiran/.feature.subscription.SubscriptionActivity","--activity-new-task","--activity-clear-top")
        if (request == listOf("raha-overlay", "enable")) return listOf("/system/bin/sh", "-c", """
set -e
/system/bin/appops set --user 0 com.abrar.voyahiran SYSTEM_ALERT_WINDOW allow
service=com.abrar.voyahiran/com.abrar.raha.feedback.RahaOverlayService
current=${'$'}(settings --user 0 get secure enabled_accessibility_services)
[ "${'$'}current" != null ] || current=
case ":${'$'}current:" in
  *":${'$'}service:"*) ;;
  *) settings --user 0 put secure enabled_accessibility_services "${'$'}{current:+${'$'}current:}${'$'}service" ;;
esac
settings --user 0 put secure accessibility_enabled 1
actual=${'$'}(settings --user 0 get secure enabled_accessibility_services)
case ":${'$'}actual:" in
  *":${'$'}service:"*) echo RAHA_OVERLAY_CONFIGURED ;;
  *) echo RAHA_OVERLAY_CONFIGURATION_FAILED; exit 1 ;;
esac
""".trimIndent())
        if (request == listOf("raha-service", "start")) return listOf("/system/bin/am", "start-foreground-service", "--user", "0",
            "-n", "com.abrar.voyahiran/.feature.voice.RahaService")
        if (request == listOf("raha-seat-ui", "show")) return request
        if (request.size == 3 && request[0] == "raha-command" &&
            com.abrar.raha.RahaAction.entries.any { it.name == request[1] } && request[2].length <= 256) return request
        if (request == listOf("roa-config", "enable")) return request
        // OEM VehicleInfoSettingActivity -> DeviceUtils.rebootH56_97System().
        if (request == listOf("vehicle-config", "restart"))
            return listOf("/system/bin/setprop", "sys.powerctl", "reboot,power")
        if (request == listOf("reboot")) return listOf("/system/bin/reboot")
        if (request.size == 2 && request[0] == "roa-hook" && request[1] in listOf("enable", "disable", "status")) return request
        if (request == listOf("ime", "current", "--user", "0")) return listOf("/system/bin/sh", "-c",
            "/system/bin/dumpsys input_method | /system/bin/grep 'mCurMethodId='")
        if (request == listOf("settings", "delete", "global", "voyah_iran_ime_override")) {
            return listOf("/system/bin/settings") + request.drop(1)
        }
        if (request.size == 5 && request.take(4) == listOf("settings", "put", "global", "voyah_iran_ime_override") &&
            Regex("(?:com\\.google\\.android\\.inputmethod\\.latin|com\\.qinggan\\.app\\.qgime)/[A-Za-z0-9_.$]+").matches(request[4])) {
            return listOf("/system/bin/settings") + request.drop(1)
        }
        if (request == listOf("id", "-u")) return listOf("/system/bin/id", "-u")
        if (request == openCamera) return listOf("/system/bin/am") + cameraLaunch.drop(1)
        if (request == openApp) return listOf("/system/bin/am") + request.drop(1)
        for (mode in 1..6) if (request == OemDriveModeWriter.arguments(mode)) {
            return listOf("/system/bin/am") + request.drop(1)
        }
        val can = listOf("service", "call", "qg.canbus", "58", "i32", "1", "i32")
        for (level in 2..4) if (request == can + listOf("401", "i32", "619", "i32", "$level")) {
            return listOf("/system/bin/service") + request.drop(1)
        }
        if (request == can + listOf("706", "i32", "923", "i32", "1")) {
            return listOf("/system/bin/service") + request.drop(1)
        }
        if (request.size == 6 && request.take(4) == listOf("service", "call", "alarm", "3") && request[4] == "s16") {
            val zone = request[5]
            if (zone == "GMT+03:30" || zone in java.util.TimeZone.getAvailableIDs()) {
                return listOf("/system/bin/service") + request.drop(1)
            }
        }
        if (request == listOf("settings", "delete", "global", "auto_time_zone") ||
            request == listOf("settings", "put", "global", "auto_time_zone", "0") ||
            request == listOf("settings", "put", "global", "auto_time_zone", "1")) {
            return listOf("/system/bin/settings") + request.drop(1)
        }
        if (request.size == 5 && request[0] == "ime" && request[1] in listOf("enable", "set") &&
            request[2] == "--user" && request[3] == "0" &&
            Regex("(?:com\\.google\\.android\\.inputmethod\\.latin|com\\.qinggan\\.app\\.qgime(?:\\.second)?)/[A-Za-z0-9_.$]+").matches(request[4])) {
            // Android 9's handler consumes the next argument as the ID; no --user support.
            // Bypass OEM ime wrappers and address only the primary input-method service.
            return if (sdkInt <= 28) listOf("/system/bin/sh", "-c",
                "[ \"\$(/system/bin/am get-current-user)\" = 0 ] || exit 1; exec /system/bin/cmd input_method ime " + request[1] + " '" + request[4] + "'")
            else listOf("/system/bin/cmd", "input_method", "ime") + request.drop(1)
        }
        if (request.size == 5 && request.take(4) == listOf("pm", "uninstall", "--user", "0") &&
            request[4] != "com.abrar.voyahiran" &&
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(request[4])) {
            return listOf("uninstall-user-app", request[4])
        }
        if (request.size == 5 && request.take(4) == listOf("am", "force-stop", "--user", "0") &&
            request[4] != "com.abrar.voyahiran" &&
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(request[4])) {
            return listOf("force-stop-user-app", request[4])
        }
        val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        if (request.size == 6 && request[0] == "pm" && request[1] in listOf("grant", "revoke") &&
            request[2] == "--user" && request[3] == "0" && packagePattern.matches(request[4]) &&
            Regex("[A-Za-z][A-Za-z0-9_.]*").matches(request[5])) {
            return listOf("/system/bin/pm") + request.drop(1)
        }
        if (request.size in 7..8 && request.take(2) == listOf("cmd", "appops") &&
            request[2] in listOf("get", "set") && request[3] == "--user" && request[4] == "0" &&
            packagePattern.matches(request[5]) && request[6] in listOf("REQUEST_INSTALL_PACKAGES", "RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND") &&
            ((request[2] == "get" && request.size == 7) ||
             (request[2] == "set" && request.size == 8 && request[7] in listOf("allow", "deny", "ignore", "default", "foreground")))) {
            return listOf("/system/bin/cmd") + request.drop(1)
        }
        if (request.size == 4 && request.take(3) == listOf("dumpsys", "deviceidle", "whitelist") &&
            request[3].firstOrNull() in listOf('+', '-', '=') && packagePattern.matches(request[3].drop(1))) {
            return listOf("/system/bin/dumpsys") + request.drop(1)
        }
        return null
    }
}





