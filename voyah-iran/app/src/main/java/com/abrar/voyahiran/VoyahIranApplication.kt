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
    val appDisplay by lazy { com.abrar.voyahiran.feature.settings.presentation.AppDisplayController(this) }
    val subscriptions by lazy { com.abrar.voyahiran.feature.subscription.SubscriptionManager(this) }
    override fun onCreate() {
        super.onCreate()
        com.abrar.voyahiran.feature.voice.RahaPhonebook.start(this)
        com.abrar.voyahiran.core.bridge.BridgeClient.configure(filesDir)
        com.abrar.voyahiran.feature.display.ScreenSwapControl(this).start()
        appDisplay.refresh()
        com.abrar.voyahiran.feature.navigation.NeshanConfigStore.refresh(this)
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.data?.schemeSpecificPart == com.abrar.voyahiran.feature.navigation.NavigationPreferences.NESHAN)
                    com.abrar.voyahiran.feature.navigation.NeshanConfigStore.installedPackageChanged(context)
            }
        }, android.content.IntentFilter(android.content.Intent.ACTION_PACKAGE_REPLACED).apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }
    val timeZoneRepository: TimeZoneRepository by lazy { SystemTimeZoneRepository(AndroidSystemTimeZonePort(this, ProcessCommandRunner())) }
    val starBackup by lazy { PreferencesStarBackup(getSharedPreferences("steering_recovery", MODE_PRIVATE)) }
    val steeringKeys by lazy { QingganStarMapping(this) }
    val steeringController by lazy { SteeringController(StarBackRepository(steeringKeys, starBackup)) { subscriptions.allowed } }
    val driveMemoryController by lazy {
        DriveMemoryController(this, PreferencesDriveMemoryStore(getSharedPreferences("drive_memory", MODE_PRIVATE)),
            PreferencesDriveMemoryStore(getSharedPreferences("energy_recovery_memory", MODE_PRIVATE)),
            PreferencesDriveMemoryStore(getSharedPreferences("aeb_memory", MODE_PRIVATE)),
            PreferencesDriveMemoryStore(getSharedPreferences("pedestrian_memory", MODE_PRIVATE)))
    }
}
