package com.abrar.voyahiran.feature.navigation

import android.content.Context
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NavigationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("display_media_map", Context.MODE_PRIVATE)
    var musicPlayer: String
        get() = prefs.getString("musicPlayer", OEM_MUSIC).orEmpty().ifBlank { OEM_MUSIC }
        set(value) { check(prefs.edit().putString("musicPlayer", value.ifBlank { OEM_MUSIC }).commit()) }
    var navigator: String
        get() = prefs.getString("navigator", "").orEmpty()
        set(value) { check(prefs.edit().putString("navigator", value).commit()) }
    var musicAutoPlay: Boolean
        get() = prefs.getBoolean("musicAutoPlay", false)
        set(value) { check(prefs.edit().putBoolean("musicAutoPlay", value).commit()) }
    var musicPauseOnDoor: Boolean
        get() = prefs.getBoolean("musicPauseOnDoor", false)
        set(value) { check(prefs.edit().putBoolean("musicPauseOnDoor", value).commit()) }
    var neshanDriving: Boolean
        get() = prefs.getBoolean("neshanDriving", false)
        set(value) { check(prefs.edit().putBoolean("neshanDriving", value).commit()) }
    val autoDriving get() = navigator == NESHAN && neshanDriving
    var neshanVoiceNavigation: Boolean
        get() = prefs.getBoolean("neshanVoiceNavigation", false)
        set(value) { check(prefs.edit().putBoolean("neshanVoiceNavigation", value).commit()) }
    val voiceNavigationEnabled get() = navigator == NESHAN && neshanVoiceNavigation
    var neshanCluster: Boolean
        get() = prefs.getBoolean("neshanCluster", false)
        set(value) { check(prefs.edit().putBoolean("neshanCluster", value).commit()) }
    val clusterEnabled get() = navigator == NESHAN && neshanCluster

    companion object {
        const val NESHAN = "org.rajman.neshan.traffic.tehran.navigator"
        const val OEM_MUSIC = "com.qinggan.media"
        const val OEM_RADIO = "com.qinggan.media#radio"
        suspend fun provision(context: Context) {
            provisionCluster(context)
            provisionAccessibility(context)
        }
        internal suspend fun provisionAccessibility(context: Context) {
            val preferences = NavigationPreferences(context)
            if (!preferences.autoDriving && !preferences.voiceNavigationEnabled) return
            val result = withContext(Dispatchers.IO) {
                ProcessCommandRunner().run(listOf("su", "0", "neshan-access", "enable"), 10)
            }
            check(!result.timedOut && result.exitCode == 0 && result.output.contains("NESHAN_ACCESS_CONFIGURED")) {
                "Neshan accessibility setup failed: ${result.output}"
            }
        }
        suspend fun provisionCluster(context: Context) {
            val enabled = NavigationPreferences(context).clusterEnabled
            val result = withContext(Dispatchers.IO) {
                ProcessCommandRunner().run(listOf("su", "0", "neshan-direct", if (enabled) "enable" else "disable"), 10)
            }
            check(result.exitCode == 0 && !result.timedOut && result.output.contains("NESHAN_DIRECT=")) {
                "Neshan direct bridge setup failed; update installer: ${result.output}"
            }
        }
    }
}
