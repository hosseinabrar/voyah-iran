package com.abrar.voyahiran.feature.navigation

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.abrar.voyahiran.R
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.databinding.PageDisplayMediaMapBinding
import com.abrar.voyahiran.feature.permissions.InstalledApp
import com.abrar.voyahiran.feature.permissions.InstalledAppAdapter
import com.abrar.voyahiran.feature.permissions.PermissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayMediaMapController(private val activity: ComponentActivity, private val view: PageDisplayMediaMapBinding) {
    private val preferences = NavigationPreferences(activity)
    private lateinit var savedPlacesButton: android.widget.Button
    private fun screenSwapEnabled() = com.abrar.voyahiran.core.compat.VehiclePlatform.screenSwap && android.provider.Settings.Global.getInt(activity.contentResolver, "voyah_iran_screen_swap", 1) == 1
    private fun passengerAppsEnabled() = com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkHooks && android.provider.Settings.Global.getInt(activity.contentResolver, "voyah_iran_passenger_apps", 1) == 1
    fun bind() {
        fun updateClusterOptions() {
            view.launcherClusterOptions.visibility = if(com.abrar.voyahiran.feature.launcher.LauncherPreferences.enabled(activity)) View.VISIBLE else View.GONE
            view.launcherClusterMap.isChecked = com.abrar.voyahiran.feature.launcher.LauncherPreferences.clusterMap(activity)
        }
        updateClusterOptions()
        view.launcherClusterMap.setOnClickListener {
            com.abrar.voyahiran.feature.launcher.LauncherPreferences.setClusterMap(activity,view.launcherClusterMap.isChecked)
        }
        view.customLauncher.isChecked = com.abrar.voyahiran.feature.launcher.LauncherPreferences.enabled(activity)
        view.customLauncher.setOnClickListener {
            val desired = view.customLauncher.isChecked
            view.customLauncher.isEnabled = false
            view.customLauncherError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try { com.abrar.voyahiran.feature.launcher.LauncherPreferences.set(activity, desired) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    view.customLauncher.isChecked = com.abrar.voyahiran.feature.launcher.LauncherPreferences.enabled(activity)
                    view.customLauncherError.text = e.message
                    view.customLauncherError.visibility = View.VISIBLE
                } finally { view.customLauncher.isEnabled = true; updateClusterOptions() }
            }
        }
        AppThemeController.bind(activity, view)
        AppDensityController.bind(activity, view)
        view.passengerDisplayOptions.visibility = if (com.abrar.voyahiran.core.compat.VehiclePlatform.isH97a) View.GONE else View.VISIBLE
        view.screenSwap.isEnabled = com.abrar.voyahiran.core.compat.VehiclePlatform.screenSwap
        view.passengerApps.isEnabled = com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkHooks
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.screenSwap) {
            view.screenSwapError.text = if (android.os.Build.VERSION.SDK_INT == 28)
                "انتقال برنامه در این نسخهٔ خودرو به‌دلیل محدودیت کیبورد نمایشگر شاگرد موقتاً غیرفعال است."
                else com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkUnavailable
            view.screenSwapError.visibility = View.VISIBLE
        }
        if (!com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkHooks) {
            view.passengerAppsError.text = com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkUnavailable
            view.passengerAppsError.visibility = View.VISIBLE
        }
        val check=view.neshanVoiceNavigation
        val parent=check.parent as android.widget.LinearLayout
        val index=parent.indexOfChild(check)
        parent.removeView(check)
        val row=android.widget.LinearLayout(activity).apply { gravity=android.view.Gravity.CENTER_VERTICAL }
        row.addView(check,android.widget.LinearLayout.LayoutParams(0,-2,1f))
        savedPlacesButton=android.widget.Button(activity).apply {
            text="مکان‌های منتخب";isAllCaps=false
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList=android.content.res.ColorStateList.valueOf(activity.getColor(R.color.confirm_green))
            setOnClickListener {SavedPlacesDialog(activity).show()}
        }
        row.addView(savedPlacesButton,android.widget.LinearLayout.LayoutParams(-2,-2).apply {marginStart=(12*activity.resources.displayMetrics.density).toInt()})
        parent.addView(row,index)
        view.screenSwap.isChecked = screenSwapEnabled()
        view.screenSwap.setOnClickListener {
            val requested = view.screenSwap.isChecked
            view.screenSwap.isEnabled = false
            view.screenSwapError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                            listOf("su", "0", "screen-swap", if (requested) "enable" else "disable"), 10)
                    }
                    check(result.exitCode == 0 && !result.timedOut && screenSwapEnabled() == requested) { "Screen swap preference not confirmed" }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    view.screenSwapError.visibility = View.VISIBLE
                    AppLog.error("ScreenSwap", "preference update failed", e)
                } finally {
                    view.screenSwap.isChecked = screenSwapEnabled()
                    view.screenSwap.isEnabled = true
                }
            }
        }
        view.passengerApps.isChecked = passengerAppsEnabled()
        view.passengerApps.setOnClickListener {
            val requested = view.passengerApps.isChecked
            view.passengerApps.isEnabled = false
            view.passengerAppsError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                            listOf("su", "0", "passenger-apps", if (requested) "enable" else "disable"), 10)
                    }
                    check(result.exitCode == 0 && !result.timedOut && passengerAppsEnabled() == requested) { "Screen swap preference not confirmed" }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    view.passengerAppsError.visibility = View.VISIBLE
                    AppLog.error("PassengerApps", "preference update failed", e)
                } finally {
                    view.passengerApps.isChecked = passengerAppsEnabled()
                    view.passengerApps.isEnabled = true
                }
            }
        }
        view.neshanVoiceNavigation.setOnClickListener {
            val previous = preferences.neshanVoiceNavigation
            preferences.neshanVoiceNavigation = view.neshanVoiceNavigation.isChecked
            view.neshanVoiceNavigation.isEnabled = false
            view.navigationError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try { NavigationPreferences.provisionAccessibility(activity) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    preferences.neshanVoiceNavigation = previous
                    view.navigationError.visibility = View.VISIBLE
                    AppLog.error("NeshanRoute", "accessibility setup failed", e)
                } finally { view.neshanVoiceNavigation.isEnabled = true; renderNeshan() }
            }
        }
        renderMusic()
        view.clusterMusic.isChecked = com.abrar.voyahiran.feature.media.ClusterMusicPreferences.enabled(activity)
        view.clusterMusic.setOnClickListener {
            val requested = view.clusterMusic.isChecked
            com.abrar.voyahiran.feature.media.ClusterMusicPreferences.save(activity, requested)
            view.clusterMusic.isEnabled = false
            activity.lifecycleScope.launch {
                try { com.abrar.voyahiran.feature.media.MediaControl.provision(activity) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    com.abrar.voyahiran.feature.media.ClusterMusicPreferences.save(activity, !requested)
                    view.clusterMusic.isChecked = !requested
                    view.mediaControlsError.visibility = View.VISIBLE
                    AppLog.error("ClusterMusic", "configuration failed", e)
                } finally { view.clusterMusic.isEnabled = true }
            }
        }
        view.musicAutoPlay.setOnClickListener { saveMusicOption(true, view.musicAutoPlay.isChecked) }
        view.musicPauseOnDoor.setOnClickListener { saveMusicOption(false, view.musicPauseOnDoor.isChecked) }
        view.neshanCluster.setOnClickListener {
            val previous = preferences.neshanCluster
            preferences.neshanCluster = view.neshanCluster.isChecked
            view.neshanCluster.isEnabled = false
            view.navigationError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try { NavigationPreferences.provisionCluster(activity) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    preferences.neshanCluster = previous
                    view.navigationError.visibility = View.VISIBLE
                    AppLog.error("NeshanCluster", "setup failed", e)
                } finally {
                    view.neshanCluster.isEnabled = true
                    renderNeshan()
                }
            }
        }
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { refreshApps(); view.screenSwap.isChecked = screenSwapEnabled(); view.passengerApps.isChecked = passengerAppsEnabled() }
        })
        view.neshanDriving.setOnClickListener {
            val previous = preferences.neshanDriving
            preferences.neshanDriving = view.neshanDriving.isChecked
            view.neshanDriving.isEnabled = false
            view.navigationError.visibility = View.GONE
            activity.lifecycleScope.launch {
                try { NavigationPreferences.provision(activity) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    preferences.neshanDriving = previous
                    view.navigationError.visibility = View.VISIBLE
                    AppLog.error("NeshanDriving", "setup failed", e)
                } finally {
                    view.neshanDriving.isEnabled = true
                    renderNeshan()
                }
            }
        }
        renderNeshan()
        // StateFlow emits immediately on Main.immediate: create all controls first.
        activity.lifecycleScope.launch {
            (activity.application as com.abrar.voyahiran.VoyahIranApplication).subscriptions.vipState.collect { renderMusic(); renderNeshan() }
        }
    }

    private fun refreshApps() = activity.lifecycleScope.launch {
        val (users, oem) = withContext(Dispatchers.IO) {
            val users = PermissionRepository(activity).apps()
            val oem = InstalledApp(NavigationPreferences.OEM_MUSIC, activity.getString(R.string.oem_music_player))
            users to oem
        }
        val placeholder = InstalledApp("", activity.getString(R.string.choose_default_app))
        val radio = InstalledApp(NavigationPreferences.OEM_RADIO, activity.getString(R.string.oem_radio_player))
        val availableMusic = listOfNotNull(oem, radio) + users.filter { it.packageName != oem.packageName }
        if (availableMusic.none { it.packageName == preferences.musicPlayer }) preferences.musicPlayer = NavigationPreferences.OEM_MUSIC
        bindPicker(view.musicPlayer, availableMusic, preferences.musicPlayer) {
            preferences.musicPlayer = it
            renderMusic()
        }
        bindPicker(view.navigator, listOf(placeholder) + users, preferences.navigator) {
            preferences.navigator = it
            renderNeshan()
            activity.lifecycleScope.launch {
                try { NavigationPreferences.provision(activity) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    view.navigationError.visibility = View.VISIBLE
                    AppLog.error("NeshanDriving", "navigator setup failed", e)
                }
            }
        }
    }

    private fun bindPicker(spinner: Spinner, apps: List<InstalledApp>, selected: String, save: (String) -> Unit) {
        spinner.onItemSelectedListener = null
        spinner.adapter = InstalledAppAdapter(activity, apps)
        val initial = apps.indexOfFirst { it.packageName == selected }.coerceAtLeast(0)
        spinner.setSelection(initial, false)
        var lastPosition = initial
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, child: View?, position: Int, id: Long) {
                if (position == lastPosition) return
                lastPosition = position
                save(apps[position].packageName)
            }
        }
    }

    private fun renderNeshan() {
        val selected = preferences.navigator == NavigationPreferences.NESHAN
        view.neshanVoiceNavigation.visibility = if (selected) View.VISIBLE else View.GONE
        view.neshanVoiceNavigation.isChecked = preferences.neshanVoiceNavigation
        savedPlacesButton.visibility=if(selected && preferences.neshanVoiceNavigation)View.VISIBLE else View.GONE
        view.neshanInstallNotice.visibility = if (selected) View.VISIBLE else View.GONE
        view.neshanDriving.visibility = if (selected) View.VISIBLE else View.GONE
        view.neshanDriving.isChecked = preferences.neshanDriving
        view.neshanCluster.visibility = if (selected) View.VISIBLE else View.GONE
        view.neshanCluster.isChecked = preferences.neshanCluster
        if (!selected) view.navigationError.visibility = View.GONE
    }

    private fun renderMusic() {
        val visibility = if (preferences.musicPlayer.isNotBlank()) View.VISIBLE else View.GONE
        view.musicAutoPlay.visibility = visibility
        view.musicPauseOnDoor.visibility = visibility
        view.musicAutoPlay.isChecked = preferences.musicAutoPlay
        view.musicPauseOnDoor.isChecked = preferences.musicPauseOnDoor
    }

    private fun saveMusicOption(autoPlay: Boolean, enabled: Boolean) {
        val previous = if (autoPlay) preferences.musicAutoPlay else preferences.musicPauseOnDoor
        if (autoPlay) preferences.musicAutoPlay = enabled else preferences.musicPauseOnDoor = enabled
        view.musicAutoPlay.isEnabled = false
        view.musicPauseOnDoor.isEnabled = false
        view.mediaControlsError.visibility = View.GONE
        activity.lifecycleScope.launch {
            try { com.abrar.voyahiran.feature.media.MediaControl.provision(activity) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                if (autoPlay) preferences.musicAutoPlay = previous else preferences.musicPauseOnDoor = previous
                view.mediaControlsError.visibility = View.VISIBLE
                AppLog.error("MusicAutomation", "configuration failed", e)
            } finally {
                view.musicAutoPlay.isEnabled = true
                view.musicPauseOnDoor.isEnabled = true
                renderMusic()
            }
        }
    }
}
