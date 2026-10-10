package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class BridgePolicyTest {
    @Test fun cleanMapAcceptsSelectedAppsOnlyOnVirtualDisplays() {
        val valid = listOf("launcher-map-clean", "org.rajman.neshan.traffic.tehran.navigator", "7")
        assertEquals(valid, BridgePolicy.resolve(valid))
        assertEquals(listOf("launcher-map-clean", "com.example.maps", "7"),
            BridgePolicy.resolve(listOf("launcher-map-clean", "com.example.maps", "7")))
        for ((index, value) in listOf(1 to "com.abrar.voyahiran", 1 to "bad;package", 2 to "0", 2 to "1", 2 to "10001", 2 to "7;reboot")) {
            assertNull(BridgePolicy.resolve(valid.toMutableList().apply { this[index] = value }))
        }
        assertNull(BridgePolicy.resolve(valid + "extra"))
    }

    @Test fun mapRouteIsRestrictedToNeshanAndValidCoordinates() {
        val valid=listOf("launcher-map-route","org.rajman.neshan.traffic.tehran.navigator","7","35.7361246","51.4933056")
        assertEquals(valid,BridgePolicy.resolve(valid))
        for ((index,value) in listOf(1 to "other.app",2 to "0",2 to "10001",3 to "NaN",3 to "91",4 to "Infinity",4 to "181",4 to "1;reboot")) {
            val invalid=valid.toMutableList().apply { this[index]=value }
            assertNull(BridgePolicy.resolve(invalid))
        }
        assertNull(BridgePolicy.resolve(valid+"extra"))
    }

    @Test fun launcherWatchOnlyAcceptsBoundedNumericCursor() {
        for(cursor in listOf("-1","0","1000")) {
            val request=listOf("launcher-vehicle","watch",cursor)
            assertEquals(request,BridgePolicy.resolve(request))
        }
        for(cursor in listOf("-2","all","0;reboot","999999999999999999999"))
            assertNull(BridgePolicy.resolve(listOf("launcher-vehicle","watch",cursor)))
        assertNull(BridgePolicy.resolve(listOf("launcher-vehicle","watch","0","extra")))
    }
    @Test fun appDensityOnlyAcceptsTheFivePresets() {
        for (dpi in listOf("120", "140", "160", "180", "200"))
            assertEquals(listOf("app-density", dpi), BridgePolicy.resolve(listOf("app-density", dpi)))
        for (request in listOf(listOf("app-density", "0"), listOf("app-density", "999"),
            listOf("app-density", "160;reboot"), listOf("app-density", "160", "extra")))
            assertNull(BridgePolicy.resolve(request))
    }
    @Test fun vipLeaseIsSeparateAndBounded() {
        val request=listOf("subscription-vip-session","90","2592000")
        assertEquals(request,BridgePolicy.resolve(request))
        assertNull(BridgePolicy.resolve(listOf("subscription-vip-session","91","2592000")))
        assertNull(BridgePolicy.resolve(listOf("subscription-vip-session","90","34560001")))
        assertNull(BridgePolicy.resolve(listOf("subscription-vip-session","90","-1")))
        assertNull(BridgePolicy.resolve(listOf("subscription-vip-session","90")))
    }
    @Test fun phoneOperationsAreBoundedAndCannotDialUssdOrShellText() {
        val mac="AA:BB:CC:DD:EE:FF"
        for(request in listOf(listOf("raha-phone","device"),listOf("raha-phone","read",mac,"0"),listOf("raha-phone","dial",mac,"+989121234567")))
            assertEquals(request,BridgePolicy.resolve(request))
        for(request in listOf(listOf("raha-phone","read",mac,"-1"),listOf("raha-phone","read","all","0"),
            listOf("raha-phone","dial",mac,"*123#"),listOf("raha-phone","dial",mac,"123;reboot"),listOf("raha-phone","dial",mac,"123","extra")))
            assertNull(BridgePolicy.resolve(request))
    }
    @org.junit.Test fun localProfileTestOnlyAllowsBoundedManualOperations() {
        for (op in listOf("status", "apply", "restore")) {
            val request = listOf("local-profile-test", op)
            assertEquals(request, BridgePolicy.resolve(request))
        }
        for (request in listOf(
            listOf("local-profile-test", "watch"),
            listOf("local-profile-test", "apply; reboot"),
            listOf("local-profile-test", "apply", "/private/other"),
            listOf("local-profile-test")
        )) assertNull(BridgePolicy.resolve(request))
    }

    @Test fun steeringBackIsLimitedToTheFixedCenterOperation() {
        val command = listOf("steering-back", "center")
        assertEquals(command, BridgePolicy.resolve(command))
        for (request in listOf(listOf("steering-back"), listOf("steering-back", "1"),
            listOf("steering-back", "center", "4"), listOf("input", "keyevent", "4"))) {
            assertNull(BridgePolicy.resolve(request))
        }
    }
    @Test fun mediaAccessCanOnlyGrantOurOwnListenerOnUserZero() {
        assertEquals(listOf("/system/bin/cmd", "notification", "allow_listener",
            "com.abrar.voyahiran/com.abrar.voyahiran.feature.media.SteeringMediaListener", "0"),
            BridgePolicy.resolve(listOf("media-access", "enable")))
        assertNull(BridgePolicy.resolve(listOf("media-access", "enable", "com.other.app/.Listener")))
        assertNull(BridgePolicy.resolve(listOf("media-access", "anything")))
    }

    @Test fun forceStopIsScopedToUserZeroAndExcludesSelf() {
        assertEquals(listOf("force-stop-user-app", "com.example.app"),
            BridgePolicy.resolve(listOf("am", "force-stop", "--user", "0", "com.example.app")))
        for (pkg in listOf("com.abrar.voyahiran", "com.example.app;reboot", "--all"))
            assertNull(BridgePolicy.resolve(listOf("am", "force-stop", "--user", "0", pkg)))
        assertNull(BridgePolicy.resolve(listOf("am", "force-stop", "--user", "10", "com.example.app")))
    }

    @Test fun displayPolicyOnlyAcceptsTwoBoundedOperations() {
        for (operation in listOf("enable", "disable"))
            assertEquals(listOf("app-display", operation), BridgePolicy.resolve(listOf("app-display", operation)))
        for (request in listOf(listOf("app-display"), listOf("app-display", "enable;reboot"),
            listOf("app-display", "enable", "com.other.app"), listOf("app-display", "set", "1")))
            assertNull(BridgePolicy.resolve(request))
    }

    @Test fun vehicleConfigurationRestartMatchesOemCommandExactly() {
        assertEquals(listOf("/system/bin/setprop", "sys.powerctl", "reboot,power"),
            BridgePolicy.resolve(listOf("vehicle-config", "restart")))
        assertNull(BridgePolicy.resolve(listOf("vehicle-config", "restart", "extra")))
        assertNull(BridgePolicy.resolve(listOf("vehicle-config", "recovery")))
    }

    @Test fun rearCameraConfigOnlyAllowsEnable() {
        assertEquals(listOf("roa-config", "enable"), BridgePolicy.resolve(listOf("roa-config", "enable")))
        assertNull(BridgePolicy.resolve(listOf("roa-config", "disable")))
        assertNull(BridgePolicy.resolve(listOf("roa-config", "enable", "REAR_CAMERA")))
    }
    @Test fun rebootHasNoExtraArguments() {
        assertEquals(listOf("/system/bin/reboot"), BridgePolicy.resolve(listOf("reboot")))
        assertNull(BridgePolicy.resolve(listOf("reboot", "recovery")))
        assertNull(BridgePolicy.resolve(listOf("reboot", "-p")))
        assertNull(BridgePolicy.resolve(listOf("reboot;id")))
    }
    @Test fun roaIsLimitedToFixedOperationsAndCamera() {
        for (action in listOf("status", "enable", "disable")) assertNotNull(BridgePolicy.resolve(listOf("roa-hook", action)))
        assertNull(BridgePolicy.resolve(listOf("roa-hook", "attach", "1234")))
        assertNull(BridgePolicy.resolve(listOf("roa-hook", "enable;reboot")))
        assertNotNull(BridgePolicy.resolve(BridgePolicy.openCamera))
        assertNull(BridgePolicy.resolve(BridgePolicy.openCamera + listOf("--es", "cameratype", "BACK")))
    }
    @Test fun backgroundAccessIsScopedToOnePackage() {
        for (prefix in listOf("+", "-", "=")) {
            assertNotNull(BridgePolicy.resolve(listOf("dumpsys", "deviceidle", "whitelist", prefix + "com.example.app")))
        }
        assertNull(BridgePolicy.resolve(listOf("dumpsys", "deviceidle", "disable")))
        assertNull(BridgePolicy.resolve(listOf("dumpsys", "deviceidle", "whitelist", "+com.example.app;reboot")))
        assertNotNull(BridgePolicy.resolve(listOf("cmd", "appops", "set", "--user", "0", "com.example.app", "RUN_ANY_IN_BACKGROUND", "allow")))
    }
    @Test fun permissionCommandsAreBoundedToUserZero() {
        assertNotNull(BridgePolicy.resolve(listOf("pm", "grant", "--user", "0", "com.example.app", "android.permission.CAMERA")))
        assertNotNull(BridgePolicy.resolve(listOf("pm", "revoke", "--user", "0", "com.example.app", "android.permission.CAMERA")))
        assertNull(BridgePolicy.resolve(listOf("pm", "grant", "--user", "10", "com.example.app", "android.permission.CAMERA")))
        assertNull(BridgePolicy.resolve(listOf("pm", "grant", "--user", "0", "com.example.app;reboot", "android.permission.CAMERA")))
        assertNotNull(BridgePolicy.resolve(listOf("cmd", "appops", "set", "--user", "0", "com.example.app", "REQUEST_INSTALL_PACKAGES", "deny")))
        assertNull(BridgePolicy.resolve(listOf("cmd", "appops", "set", "--user", "0", "com.example.app", "CAMERA", "allow")))
    }
    @Test fun readsOnlyThePrimaryInputMethodService() {
        val command = BridgePolicy.resolve(listOf("ime", "current", "--user", "0"))!!
        assertTrue(command.last().contains("dumpsys input_method |"))
        assertFalse(command.last().contains("input_method_ext"))
        assertFalse(command.last().contains("-m 1")) // Drain dumpsys; avoid system-server EPIPE.
        assertNull(BridgePolicy.resolve(listOf("ime", "current", "--user", "10")))
        assertNull(BridgePolicy.resolve(listOf("ime", "current", "--user", "0", ";reboot")))
    }
    @Test fun keyboardCommandsUseVersionSpecificPrimaryServiceSyntax() {
        val id = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        for (operation in listOf("enable", "set")) {
            val request = listOf("ime", operation, "--user", "0", id)
            val legacy = BridgePolicy.resolve(request, 28)!!
            assertEquals(listOf("/system/bin/sh", "-c"), legacy.take(2))
            assertTrue(legacy.last().contains("get-current-user"))
            assertTrue(legacy.last().endsWith("exec /system/bin/cmd input_method ime $operation '$id'"))
            assertFalse(legacy.last().contains("--user"))
            assertEquals(listOf("/system/bin/cmd", "input_method", "ime", operation, "--user", "0", id), BridgePolicy.resolve(request, 30))
            assertNull(BridgePolicy.resolve(request.dropLast(1) + "$id'; reboot", 28))
            assertNull(BridgePolicy.resolve(listOf("ime", operation, "--user", "10", id), 28))
        }
    }
    @Test fun overrideCannotSelectPassengerOrInjectCommands() {
        val prefix = listOf("settings", "put", "global", "voyah_iran_ime_override")
        for (id in listOf("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME", "com.qinggan.app.qgime/.QingganIME")) {
            assertEquals(listOf("/system/bin/settings") + (prefix + id).drop(1), BridgePolicy.resolve(prefix + id))
        }
        for (id in listOf("com.qinggan.app.qgime.second/com.qinggan.app.qgime.QingganIME", "attacker/.IME", "com.qinggan.app.qgime/.IME;reboot")) {
            assertNull(BridgePolicy.resolve(prefix + id))
        }
        assertNotNull(BridgePolicy.resolve(listOf("settings", "delete", "global", "voyah_iran_ime_override")))
    }
    @Test fun acceptsExistingDriveModesOnly() {
        for (mode in 1..6) assertEquals("/system/bin/am", BridgePolicy.resolve(OemDriveModeWriter.arguments(mode))?.first())
        assertNull(BridgePolicy.resolve(OemDriveModeWriter.arguments(1).dropLast(1) + "7"))
    }
    @Test fun noArbitraryShellOrRootExecution() {
        for (args in listOf(listOf("sh", "-c", "id"), listOf("su", "0", "id"), listOf("setenforce", "0"), listOf("rm", "-rf", "/"))) {
            assertNull(BridgePolicy.resolve(args))
        }
    }
    @Test fun onlyExplicitCanFieldsAndValues() {
        val prefix = listOf("service", "call", "qg.canbus", "58", "i32", "1", "i32")
        for (level in 2..4) assertNotNull(BridgePolicy.resolve(prefix + listOf("401", "i32", "619", "i32", "$level")))
        assertNull(BridgePolicy.resolve(prefix + listOf("401", "i32", "619", "i32", "5")))
        assertNotNull(BridgePolicy.resolve(prefix + listOf("706", "i32", "923", "i32", "1")))
        assertNull(BridgePolicy.resolve(prefix + listOf("706", "i32", "923", "i32", "2")))
    }
    @Test fun cannotAlterSubscriptionOrOtherSettings() {
        assertNull(BridgePolicy.resolve(listOf("settings", "put", "global", "adb_enabled", "1")))
        assertNotNull(BridgePolicy.resolve(listOf("settings", "delete", "global", "auto_time_zone")))
    }
    @Test fun timezoneIsValidatedBeforeExecution() {
        val prefix = listOf("service", "call", "alarm", "3", "s16")
        assertNotNull(BridgePolicy.resolve(prefix + "GMT+03:30"))
        assertNotNull(BridgePolicy.resolve(prefix + "Asia/Tehran"))
        assertNull(BridgePolicy.resolve(prefix + "Asia/Tehran; reboot"))
    }
    @Test fun keyboardRestrictedToMainUserAndKnownPackages() {
        assertNotNull(BridgePolicy.resolve(listOf("ime", "set", "--user", "0", "com.qinggan.app.qgime.second/com.qinggan.app.qgime.QingganIME")))
        assertNull(BridgePolicy.resolve(listOf("ime", "set", "--user", "10", "com.google.android.inputmethod.latin/.LatinIME")))
        assertNull(BridgePolicy.resolve(listOf("ime", "set", "--user", "0", "attacker/.IME")))
    }
    private val token = "a".repeat(64)
    private fun wire(auth: String = token, count: Int = 2, arg: String = "id"): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(BridgePolicy.MAGIC); it.writeUTF(auth)
            it.writeUTF("12345678-1234-1234-1234-123456789abc")
            it.writeInt(8); it.writeInt(count); it.writeUTF(arg); it.writeUTF("-u")
        }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
    @Test fun authenticatedRequestRetainsCorrelation() {
        val result = BridgeProtocol.read(wire(), token)
        assertEquals(listOf("id", "-u"), result.arguments)
        assertEquals(8L, result.timeout)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongTokenBeforeCommands() { BridgeProtocol.read(wire(auth = "b".repeat(64)), token) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnboundedArgumentCount() { BridgeProtocol.read(wire(count = 100000), token) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedArgument() { BridgeProtocol.read(wire(arg = "x".repeat(257)), token) }
}



