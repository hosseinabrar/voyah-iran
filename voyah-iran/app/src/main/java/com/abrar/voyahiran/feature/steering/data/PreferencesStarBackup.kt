package com.abrar.voyahiran.feature.steering.data

import android.content.SharedPreferences
import com.abrar.voyahiran.feature.steering.domain.StarBackup
import com.abrar.voyahiran.feature.steering.domain.SteeringException
import com.abrar.voyahiran.feature.steering.domain.SteeringFailure

class PreferencesStarBackup(private val preferences: SharedPreferences) : StarBackup {
    // Preserve user intent independently from the rollback journal and current Binder connection.
    var desired: Boolean
        get() = preferences.getBoolean(DESIRED, read() != null && !restoring)
        set(value) {
            if (!preferences.edit().putBoolean(DESIRED, value).commit()) throw SteeringException(SteeringFailure.STORAGE)
        }
    override fun read(): Int? = if (preferences.contains(PREVIOUS)) preferences.getInt(PREVIOUS, 0) else null

    override fun save(previous: Int) {
        if (!preferences.edit().putInt(PREVIOUS, previous).putBoolean(RESTORING, false).commit()) {
            throw SteeringException(SteeringFailure.STORAGE)
        }
    }

    override fun clear() {
        if (!preferences.edit().remove(PREVIOUS).remove(RESTORING).commit()) {
            throw SteeringException(SteeringFailure.STORAGE)
        }
    }

    override val restoring: Boolean get() = preferences.getBoolean(RESTORING, false)
    override fun markRestoring() {
        if (!preferences.edit().putBoolean(RESTORING, true).commit()) throw SteeringException(SteeringFailure.STORAGE)
    }
    private companion object {
        const val DESIRED = "star_back_desired_v2"
        const val PREVIOUS = "star_previous_mapping_v1"
        const val RESTORING = "restore_pending_v1"
    }
}

