package com.abrar.voyahiran.feature.launcher

/** Independent task: opening the passenger drawer must never move the center drawer. */
class PassengerAppsActivity : LauncherAppsActivity() {
    override val launcherDisplayId: Int = 1
}
