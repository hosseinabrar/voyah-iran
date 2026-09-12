package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.feature.drivememory.data.OemDriveModeWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class BridgePolicyTest {
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
        assertNull(BridgePolicy.resolve(listOf("ime", "current", "--user", "10")))
        assertNull(BridgePolicy.resolve(listOf("ime", "current", "--user", "0", ";reboot")))
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



