package com.abrar.voyahiran.feature.recoverymemory.domain

/** Holds recovery memory untouched until drive-mode restoration and its immediate effects settle. */
class RecoveryRestoreOrder {
    private var resumeAt = 0L
    fun allow(driveEnabled: Boolean, drivePending: Boolean, driveReady: Boolean, off: Boolean, now: Long): Boolean {
        if (driveEnabled && drivePending) resumeAt = now + 1000
        return off || !driveEnabled || (driveReady && now >= resumeAt)
    }
}
