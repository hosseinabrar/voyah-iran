package com.abrar.voyahiran.feature.rearcamera.data

import android.content.Context
import com.abrar.voyahiran.core.shell.CommandRunner
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.CanDriveGateway
import com.abrar.voyahiran.feature.rearcamera.domain.RearCameraPort
import com.abrar.voyahiran.feature.rearcamera.domain.RearCameraSnapshot

class OemRearCameraPort(context: Context, private val runner: CommandRunner = ProcessCommandRunner()) : RearCameraPort {
    private val gateway = CanDriveGateway(context) { }

    override suspend fun read(): RearCameraSnapshot {
        // Same hardware-function signal used by OEM CameraStatusManager; no property is modified.
        val dvr = property("vendor.camera.dvr.status") == "lock"
        com.abrar.voyahiran.core.logging.AppLog.changed("CameraDVR", "available=$dvr")
        if (!dvr) return RearCameraSnapshot(false, false, null)
        val camera = property("vendor.camera.roa.status") == "lock"
        com.abrar.voyahiran.core.logging.AppLog.changed("CameraROA", "available=$camera")
        if (!camera) return RearCameraSnapshot(true, false, null)
        return try {
            val status = gateway.readRearCameraStatus()
            val hook = runner.run(listOf("su", "0", "roa-hook", "status"), 20)
            val ready = hook.exitCode == 0 && !hook.timedOut && hook.output.trim() in listOf("ROA_HOOK=ATTACHED", "ROA_HOOK=WAITING")
            com.abrar.voyahiran.core.logging.AppLog.changed("RearCameraHook", "status=$status hook=${hook.output} ready=$ready")
            RearCameraSnapshot(true, true, status, ready)
        }
        finally { gateway.close() }
    }

    private fun property(name: String): String {
        val result = runner.run(listOf("getprop", name), 3)
        check(result.exitCode == 0 && !result.timedOut) { "Camera capabilities unavailable" }
        return result.output.trim()
    }

    override suspend fun configureSwitch(enabled: Boolean) {
        val result = runner.run(listOf("su", "0", "roa-hook", if (enabled) "enable" else "disable"), 25)
        val accepted = if (enabled) listOf("ROA_HOOK=ATTACHED", "ROA_HOOK=WAITING") else listOf("ROA_HOOK=DISABLED")
        check(result.exitCode == 0 && !result.timedOut && result.output.trim() in accepted) { "ROA switch not confirmed: ${result.output}" }
        if (enabled) {
            val config = runner.run(listOf("su", "0", "roa-config", "enable"), 20)
            check(config.exitCode == 0 && !config.timedOut && config.output.trim() in
                listOf("RPM_CONFIG=ENABLED", "RPM_CONFIG=REBOOT_REQUIRED")) {
                "ROA vehicle configuration not confirmed: ${config.output}"
            }
        }
    }

    override suspend fun toggle() {
        com.abrar.voyahiran.core.logging.AppLog.info("RearCamera", "sending-single-toggle awaiting-status")
        // ICanBusService.setVehicleState(ROA_CAMERA_CTL, 1); SDK ordinal and value verified locally.
        val result = runner.run(listOf("su", "0", "service", "call", "qg.canbus", "58",
            "i32", "1", "i32", "706", "i32", "923", "i32", "1"), 8)
        check(result.exitCode == 0 && !result.timedOut &&
            Regex("Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000\\b").containsMatchIn(result.output)) {
            "Rear camera request failed"
        }
    }
}

