package com.abrar.voyahiran.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.abrar.voyahiran.BuildConfig
import com.abrar.voyahiran.R
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.databinding.ActivityMainBinding
import com.abrar.voyahiran.feature.settings.domain.TimeZoneFailure
import com.abrar.voyahiran.feature.settings.domain.TimeZoneMode
import com.abrar.voyahiran.feature.settings.presentation.SettingsUiState
import com.abrar.voyahiran.feature.settings.presentation.SettingsViewModel
import com.abrar.voyahiran.feature.settings.presentation.RebootState
import com.abrar.voyahiran.feature.steering.domain.SteeringFailure
import com.abrar.voyahiran.feature.steering.presentation.SteeringUiState
import com.abrar.voyahiran.feature.steering.service.StarBackService
import com.abrar.voyahiran.feature.drivememory.domain.DrivePhase
import com.abrar.voyahiran.feature.drivememory.presentation.DriveMemoryUiState
import com.abrar.voyahiran.feature.drivememory.service.DriveMemoryService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var projectNotice: ProjectNoticeDialog? = null

    override fun onResume() {
        super.onResume()
        if (projectNotice?.isShowing != true) {
            projectNotice = ProjectNoticeDialog(this).also { it.show() }
        }
    }

    override fun onPause() {
        projectNotice?.dismiss()
        projectNotice = null
        super.onPause()
    }
    private val reboot: com.abrar.voyahiran.feature.settings.presentation.RebootViewModel by viewModels()
    private val permissions: com.abrar.voyahiran.feature.permissions.PermissionsViewModel by viewModels()
    private val apollo: com.abrar.voyahiran.feature.apollo.presentation.ApolloViewModel by viewModels()
    private val keyboard: com.abrar.voyahiran.feature.keyboard.presentation.KeyboardViewModel by viewModels()
    private val rearCamera: com.abrar.voyahiran.feature.rearcamera.presentation.RearCameraViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private val model: SettingsViewModel by viewModels {
        SettingsViewModel.Factory((application as VoyahIranApplication).timeZoneRepository)
    }
    private var selectedTab = Tab.SETTINGS
    private val steering get() = (application as VoyahIranApplication).steeringController
    private var steeringRequestPending = false
    private val drive get() = (application as VoyahIranApplication).driveMemoryController
    private var driveRequestPending = false
    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.abrar.voyahiran.core.logging.AppLog.info("App", "start version=${BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT} uid=${android.os.Process.myUid()} display=${display?.displayId}")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.settingsPage.rebootDevice.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.reboot_label)
                .setMessage(R.string.reboot_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reboot_label) { _, _ -> reboot.reboot() }
                .show()
        }
        binding.settingsTab.setOnClickListener { selectTab(Tab.SETTINGS) }
        binding.comfortTab.setOnClickListener { selectTab(Tab.COMFORT) }
        binding.apolloTab.setOnClickListener { selectTab(Tab.APOLLO); apollo.refresh() }
        binding.apolloPage.refresh.setOnClickListener { apollo.refresh() }
        binding.permissionsTab.setOnClickListener { selectTab(Tab.PERMISSIONS); permissions.refresh() }
        binding.permissionsPage.refresh.setOnClickListener { permissions.refresh() }
        binding.permissionsPage.apps.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position) as? com.abrar.voyahiran.feature.permissions.InstalledApp ?: return
                permissions.select(item.packageName)
            }
        }
        binding.aboutTab.setOnClickListener { selectTab(Tab.ABOUT) }
        binding.settingsPage.timeZoneCorrection.setOnClickListener {
            model.setCorrection(binding.settingsPage.timeZoneCorrection.isChecked)
            render(model.state.value)
        }
        binding.settingsPage.retryButton.setOnClickListener { model.refresh() }
        binding.settingsPage.gboard.setOnClickListener {
            keyboard.setEnabled(binding.settingsPage.gboard.isChecked)
            renderKeyboard(keyboard.state.value)
        }
        binding.settingsPage.keyboardRetry.setOnClickListener { keyboard.refresh() }
        binding.settingsPage.rearCamera.setOnClickListener {
            rearCamera.setEnabled(binding.settingsPage.rearCamera.isChecked)
            renderRearCamera(rearCamera.state.value)
        }
        binding.settingsPage.rearCameraOpen.setOnClickListener {
            lifecycleScope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(listOf("su", "0") + com.abrar.voyahiran.core.bridge.BridgePolicy.openCamera, 15)
                }
                com.abrar.voyahiran.core.logging.AppLog.info("RearCamera", "open camera exit=${result.exitCode} output=${result.output}")
                if (result.exitCode != 0 || result.timedOut || result.output.contains("Error", true)) {
                    android.widget.Toast.makeText(this@MainActivity, R.string.rear_camera_error, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.settingsPage.rearCameraRetry.setOnClickListener { rearCamera.refresh() }
        binding.settingsPage.starBack.setOnClickListener {
            requestSteering(if (binding.settingsPage.starBack.isChecked) StarBackService.ENABLE else StarBackService.DISABLE)
        }


        binding.comfortPage.driveMemory.setOnClickListener {
            requestDrive(if (binding.comfortPage.driveMemory.isChecked) DriveMemoryService.ENABLE else DriveMemoryService.DISABLE)
        }
        binding.comfortPage.driveRetry.setOnClickListener { requestDrive(DriveMemoryService.RETRY) }
        binding.comfortPage.recoveryMemory.setOnClickListener {
            requestDrive(if (binding.comfortPage.recoveryMemory.isChecked) DriveMemoryService.RECOVERY_ENABLE else DriveMemoryService.RECOVERY_DISABLE)
        }
        binding.comfortPage.recoveryRetry.setOnClickListener { requestDrive(DriveMemoryService.RECOVERY_RETRY) }
        binding.aboutPage.version.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        selectTab(Tab.entries.firstOrNull { it.name == savedInstanceState?.getString(TAB_KEY) } ?: Tab.SETTINGS)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { reboot.state.collect { state ->
                    binding.settingsPage.rebootDevice.isEnabled = state == RebootState.IDLE || state == RebootState.UNCONFIRMED
                    binding.settingsPage.rebootStatus.visibility = if (state == RebootState.IDLE) View.GONE else View.VISIBLE
                    binding.settingsPage.rebootStatus.setText(when (state) {
                        com.abrar.voyahiran.feature.settings.presentation.RebootState.UNCONFIRMED -> R.string.reboot_unconfirmed
                        else -> R.string.reboot_pending
                    })
                } }
                launch { model.state.collect(::render) }
                launch { apollo.state.collect(::renderApollo) }
                launch { permissions.state.collect(::renderPermissions) }
                launch {
                    while (true) {
                        if (selectedTab == Tab.APOLLO) apollo.refresh()
                        delay(5000)
                    }
                }
                launch { keyboard.state.collect(::renderKeyboard) }
                launch { rearCamera.state.collect(::renderRearCamera) }
                launch { rearCamera.needsRebootPrompt.collect { show ->
                    if (show) {
                        rearCamera.acknowledgeRebootPrompt()
                        val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.rear_camera_reboot_title)
                            .setMessage(R.string.rear_camera_reboot_message)
                            .setPositiveButton(R.string.reboot_label) { _, _ -> reboot.reboot() }
                            .setNegativeButton(R.string.reboot_later, null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
                                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.confirm_green))
                                setTextColor(getColor(R.color.reboot_text))
                            }
                            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).apply {
                                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.button_gray))
                                setTextColor(getColor(R.color.reboot_text))
                            }
                        }
                        dialog.show()
                    }
                } }
                launch {
                    while (true) {
                        rearCamera.refresh()
                        delay(5000)
                    }
                }
                launch { steering.state.collect { steeringRequestPending = false; renderSteering(it) } }
                launch { drive.state.collect { driveRequestPending = false; renderDrive(it) } }
                launch {
                    while (true) {
                        updateClock(model.state.value)
                        if (!drive.state.value.anyEnabled) drive.tick()
                        delay(1000)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // This receiver only listens to protected system broadcasts, not app commands.
        registerReceiver(timeChanged, IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
        model.refresh()
        keyboard.refresh()
        if (selectedTab == Tab.PERMISSIONS) permissions.refresh()
        if ((application as VoyahIranApplication).starBackup.let { it.desired || it.read() != null }) {
            StarBackService.request(this, StarBackService.RESUME)
        } else lifecycleScope.launch { steering.refresh() }
        if (drive.state.value.anyEnabled) DriveMemoryService.request(this, DriveMemoryService.RESUME)
        else lifecycleScope.launch { drive.start() }
    }

    override fun onStop() {
        unregisterReceiver(timeChanged)
        if (!drive.state.value.anyEnabled) lifecycleScope.launch { drive.detach() }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TAB_KEY, selectedTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun selectTab(tab: Tab) {
        if (tab == Tab.APOLLO || tab == Tab.ABOUT) { selectTab(Tab.SETTINGS); return }
        com.abrar.voyahiran.core.logging.AppLog.info("UI", "tab=$tab")
        selectedTab = tab
        binding.settingsTab.isSelected = tab == Tab.SETTINGS
        binding.comfortTab.isSelected = tab == Tab.COMFORT
        binding.apolloTab.isSelected = tab == Tab.APOLLO
        binding.permissionsTab.isSelected = tab == Tab.PERMISSIONS
        binding.permissionsPage.root.visibility = if (tab == Tab.PERMISSIONS) View.VISIBLE else View.GONE
        binding.aboutTab.isSelected = tab == Tab.ABOUT
        binding.settingsPage.root.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE
        binding.comfortPage.root.visibility = if (tab == Tab.COMFORT) View.VISIBLE else View.GONE
        binding.apolloPage.root.visibility = if (tab == Tab.APOLLO) View.VISIBLE else View.GONE
        binding.aboutPage.root.visibility = if (tab == Tab.ABOUT) View.VISIBLE else View.GONE
    }

    private fun render(state: SettingsUiState) = with(binding.settingsPage) {
        timeZoneCorrection.isChecked = state.snapshot?.correctionEnabled == true
        timeZoneCorrection.isEnabled = state.snapshot != null && !state.busy
        currentZone.text = state.snapshot?.zoneId ?: getString(R.string.unavailable_status)
        currentStatus.setText(when {
            state.snapshot == null -> R.string.unavailable_status
            state.snapshot.correctionEnabled -> R.string.fixed_status
            state.snapshot.zoneId == TimeZoneMode.TEHRAN.zoneId -> R.string.tehran_status
            else -> R.string.other_status
        })
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        val message = when {
            state.busy -> R.string.applying
            state.error != null -> when (state.error) {
                TimeZoneFailure.ROOT_UNAVAILABLE -> R.string.root_required
                TimeZoneFailure.ACCESS_REQUIRED -> R.string.timezone_permission_required
                TimeZoneFailure.TIMEOUT -> R.string.timeout_error
                TimeZoneFailure.READ_FAILED -> R.string.read_error
                TimeZoneFailure.WRITE_FAILED -> R.string.write_error
                TimeZoneFailure.VERIFICATION_FAILED -> R.string.verification_error
            }
            state.snapshot?.automatic == true -> R.string.auto_enabled
            state.applied -> R.string.applied
            else -> null
        }
        feedback.visibility = if (message != null) View.VISIBLE else View.GONE
        if (message != null) feedback.setText(message)
        feedback.setTextColor(getColor(if (state.error != null) R.color.error else R.color.text_secondary))
        retryButton.visibility = if (state.error != null && !state.busy) View.VISIBLE else View.GONE
        updateClock(state)
    }

    private fun updateClock(state: SettingsUiState) {
        val zone = state.snapshot?.zoneId
        binding.settingsPage.localClock.text = if (zone == null) getString(R.string.unknown_time) else {
            SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date())
        }
    }

    private fun requestSteering(action: String) {
        if (steeringRequestPending) return
        steeringRequestPending = true
        renderSteering(steering.state.value)
        try {
            StarBackService.request(this, action)
            steeringRequestPending = false
            renderSteering(steering.state.value)
        } catch (_: Exception) {
            steeringRequestPending = false
            renderSteering(steering.state.value.copy(error = SteeringFailure.CONNECTION))
        }
    }

    private fun renderKeyboard(state: com.abrar.voyahiran.feature.keyboard.presentation.KeyboardUiState) = with(binding.settingsPage) {
        gboard.isChecked = state.snapshot?.enabled == true
        gboard.isEnabled = !state.busy && state.snapshot != null
        keyboardProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
        keyboardStatus.visibility = if (state.error != null) View.VISIBLE else View.GONE
        keyboardStatus.setText(when (state.error) {
            com.abrar.voyahiran.feature.keyboard.domain.KeyboardFailure.NOT_INSTALLED -> R.string.gboard_missing
            com.abrar.voyahiran.feature.keyboard.domain.KeyboardFailure.NO_RESTORE -> R.string.keyboard_no_restore
            else -> R.string.keyboard_error
        })
        keyboardRetry.visibility = if (state.error != null && !state.busy) View.VISIBLE else View.GONE
    }

    private fun renderRearCamera(state: com.abrar.voyahiran.feature.rearcamera.presentation.RearCameraUiState) = with(binding.settingsPage) {
        rearCameraCard.visibility = if (state.snapshot?.dvrAvailable == true) View.VISIBLE else View.GONE
        rearCamera.isChecked = state.snapshot?.enabled == true
        rearCamera.isEnabled = state.snapshot?.canChange == true && !state.busy
        rearCameraOpen.visibility = if (state.snapshot?.enabled == true && !state.busy) View.VISIBLE else View.GONE
        rearCameraProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
        val failed = state.error || (state.snapshot?.status == 2 && !state.snapshot.switchReady) || (state.snapshot?.dvrAvailable == true && state.snapshot.canChange.not())
        rearCameraStatus.visibility = if (failed && !state.busy) View.VISIBLE else View.GONE
        rearCameraRetry.visibility = if (failed && !state.busy) View.VISIBLE else View.GONE
    }

    private fun renderSteering(state: SteeringUiState) = with(binding.settingsPage) {
        val desired = (application as VoyahIranApplication).starBackup.desired
        starBack.isChecked = desired
        starBack.isEnabled = !steeringRequestPending
        val pending = if (desired) state.snapshot?.enabled != true else state.snapshot?.previous != null
        starProgress.visibility = if (pending && state.busy) View.VISIBLE else View.GONE
        val message = when {
            desired && state.snapshot?.enabled != true -> R.string.star_connecting
            !desired && state.snapshot?.previous != null -> R.string.star_restoring
            state.error != null -> R.string.star_connection_error
            else -> null
        }
        starStatus.visibility = if (message != null) View.VISIBLE else View.GONE
        message?.let(starStatus::setText)
    }
    private fun requestDrive(action: String) {
        if (driveRequestPending || drive.state.value.busy) return
        driveRequestPending = true
        renderDrive(drive.state.value)
        try { DriveMemoryService.request(this, action) }
        catch (_: RuntimeException) {
            driveRequestPending = false
            renderDrive(drive.state.value.copy(error = true))
        }
    }

    private fun renderDrive(state: DriveMemoryUiState) = with(binding.comfortPage) {
        val busy = state.busy || driveRequestPending
        driveMemory.isChecked = state.enabled
        driveMemory.isEnabled = !busy && (state.enabled || state.current != null)
        driveProgress.visibility = if (busy || state.phase == DrivePhase.RESTORING) View.VISIBLE else View.GONE
        val message = when {
            state.phase == DrivePhase.FAILED -> R.string.drive_unconfirmed
            state.error -> R.string.drive_error
            busy || (state.enabled && state.phase == DrivePhase.WAITING) -> R.string.drive_waiting
            state.phase == DrivePhase.RESTORING -> R.string.drive_restoring
            else -> null
        }
        driveStatus.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(driveStatus::setText)
        driveRetry.visibility = if (state.error && !busy) View.VISIBLE else View.GONE
        val recovery = state.recovery
        recoveryMemory.isChecked = recovery.enabled
        recoveryMemory.isEnabled = !busy && (recovery.enabled || recovery.current != null)
        recoveryProgress.visibility = if (busy || recovery.phase == DrivePhase.RESTORING) View.VISIBLE else View.GONE
        val recoveryMessage = when {
            recovery.phase == DrivePhase.FAILED -> R.string.recovery_unconfirmed
            recovery.error -> R.string.drive_error
            busy || (recovery.enabled && recovery.phase == DrivePhase.WAITING) -> R.string.drive_waiting
            recovery.phase == DrivePhase.RESTORING -> R.string.recovery_restoring
            else -> null
        }
        recoveryStatus.visibility = if (recoveryMessage == null) View.GONE else View.VISIBLE
        recoveryMessage?.let(recoveryStatus::setText)
        recoveryRetry.visibility = if (recovery.error && !busy) View.VISIBLE else View.GONE
    }

    private fun renderApollo(state: com.abrar.voyahiran.feature.apollo.presentation.ApolloUiState) = with(binding.apolloPage) {
        fun show(box: android.widget.CheckBox, title: Int, value: Boolean?) {
            box.isChecked = value == true
            box.text = if (value == null) getString(R.string.apollo_unknown, getString(title)) else getString(title)
        }
        show(subscription, R.string.apollo_subscription, state.report.subscribed)
        val features = com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.entries
        val boxes = listOf(laneChange, navigation, trafficLight, lightChange, signs, emergencySteering, emergencyLane)
        val titles = listOf(R.string.apollo_lane_change, R.string.apollo_navigation, R.string.apollo_traffic_light,
            R.string.apollo_light_change, R.string.apollo_signs, R.string.apollo_emergency_steering, R.string.apollo_emergency_lane)
        features.forEachIndexed { index, feature -> show(boxes[index], titles[index], state.report.features[feature]) }
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        refresh.isEnabled = !state.busy
    }

    private var permissionApps: List<com.abrar.voyahiran.feature.permissions.InstalledApp> = emptyList()
    private fun renderPermissions(state: com.abrar.voyahiran.feature.permissions.PermissionsState) = with(binding.permissionsPage) {
        if (permissionApps != state.apps) {
            permissionApps = state.apps
            apps.adapter = com.abrar.voyahiran.feature.permissions.InstalledAppAdapter(this@MainActivity, state.apps)
        }
        val position = state.apps.indexOfFirst { it.packageName == state.selected }
        if (position >= 0 && apps.selectedItemPosition != position) apps.setSelection(position)
        apps.isEnabled = !state.busy
        refresh.isEnabled = !state.busy
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        feedback.visibility = if (state.error) View.VISIBLE else View.GONE
        val boxes = listOf("microphone" to permissionMicrophone, "camera" to permissionCamera,
            "location" to permissionLocation, "install" to permissionInstall, "background" to permissionBackground)
        boxes.forEach { (key, box) ->
            val permission = state.permissions.firstOrNull { it.name == key }
            box.isChecked = permission?.granted == true
            box.isEnabled = !state.busy && permission?.available == true
            box.alpha = if (permission?.available == true) 1f else 0.4f
            box.setOnClickListener {
                if (permission != null) {
                    val wanted = box.isChecked
                    box.isChecked = permission.granted
                    permissions.set(permission, wanted)
                }
            }
        }
        val available = state.permissions.filter { it.available }
        val allGranted = available.isNotEmpty() && available.all { it.granted }
        permissionAll.isChecked = allGranted
        permissionAll.isEnabled = !state.busy && available.isNotEmpty()
        permissionAll.setOnClickListener {
            val wanted = permissionAll.isChecked
            permissionAll.isChecked = allGranted
            permissions.set(com.abrar.voyahiran.feature.permissions.AppPermission("all", "", allGranted), wanted)
        }
    }
    private enum class Tab { SETTINGS, COMFORT, APOLLO, PERMISSIONS, ABOUT }
    companion object { private const val TAB_KEY = "selected_tab" }
}







