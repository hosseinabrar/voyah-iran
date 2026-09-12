package com.abrar.voyahiran.feature.drivememory.data

import android.content.SharedPreferences
import com.abrar.voyahiran.feature.drivememory.domain.*

class PreferencesDriveMemoryStore(private val preferences: SharedPreferences) : DriveMemoryStore {
    override fun read() = DriveMemory(
        preferences.getBoolean("enabled", false),
        preferences.getInt("mode", 0).takeIf { validDriveMode(it) },
        preferences.getString("boot", "").orEmpty(),
        preferences.getBoolean("pending", false),
    )
    override fun write(value: DriveMemory) {
        check(preferences.edit().putBoolean("enabled", value.enabled).putInt("mode", value.mode ?: 0)
            .putString("boot", value.boot).putBoolean("pending", value.pending).commit()) { "Drive memory storage failed" }
    }
}
