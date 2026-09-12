package com.abrar.voyahiran

import android.app.Application
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.settings.data.AndroidSystemTimeZonePort
import com.abrar.voyahiran.feature.settings.data.SystemTimeZoneRepository
import com.abrar.voyahiran.feature.settings.domain.TimeZoneRepository
import com.abrar.voyahiran.feature.steering.data.PreferencesStarBackup
import com.abrar.voyahiran.feature.steering.data.QingganStarMapping
import com.abrar.voyahiran.feature.steering.domain.StarBackRepository
import com.abrar.voyahiran.feature.steering.presentation.SteeringController
import com.abrar.voyahiran.feature.drivememory.data.PreferencesDriveMemoryStore
import com.abrar.voyahiran.feature.drivememory.presentation.DriveMemoryController

class VoyahIranApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.abrar.voyahiran.core.bridge.BridgeClient.configure(filesDir)
    }
    val timeZoneRepository: TimeZoneRepository by lazy { SystemTimeZoneRepository(AndroidSystemTimeZonePort(this, ProcessCommandRunner())) }
    val starBackup by lazy { PreferencesStarBackup(getSharedPreferences("steering_recovery", MODE_PRIVATE)) }
    val steeringController by lazy { SteeringController(StarBackRepository(QingganStarMapping(this), starBackup)) }
    val driveMemoryController by lazy {
        DriveMemoryController(this, PreferencesDriveMemoryStore(getSharedPreferences("drive_memory", MODE_PRIVATE)),
            PreferencesDriveMemoryStore(getSharedPreferences("energy_recovery_memory", MODE_PRIVATE)))
    }
}
