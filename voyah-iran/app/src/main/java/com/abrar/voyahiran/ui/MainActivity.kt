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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var projectNotice: ProjectNoticeDialog? = null
    private var permissionsEntry = false
    private var apolloAvailable = false
    private var avmTurnBusy = false

    private fun updateAvmTurn(operation: String) {
        if (!apolloAvailable || avmTurnBusy) return
        avmTurnBusy = true
        val views = binding.comfortPage
        views.avmTurn.isEnabled = false
        views.avmTurnRetry.visibility = View.GONE
        views.avmTurnStatus.setText(R.string.avm_turn_wait)
        lifecycleScope.launch {
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.abrar.voyahiran.feature.apollo.data.AvmTurnControl.update(this@MainActivity, operation)
                }
                val actual = Regex("AVM_TURN actual=(-?\\d+)").find(result.output)?.groupValues?.get(1)?.toIntOrNull()
                views.avmTurn.isChecked = com.abrar.voyahiran.feature.apollo.data.AvmTurnControl.checked(this@MainActivity)
                val success = result.exitCode == 0 && !result.timedOut && actual in 1..2
                views.avmTurnStatus.setText(if (success) R.string.avm_turn_read_ok else R.string.avm_turn_failed)
                views.avmTurnRetry.visibility = if (success) View.GONE else View.VISIBLE
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("AvmTurn", "UI operation=$operation failed", e)
                views.avmTurn.isChecked = com.abrar.voyahiran.feature.apollo.data.AvmTurnControl.checked(this@MainActivity)
                views.avmTurnStatus.setText(R.string.avm_turn_failed)
                views.avmTurnRetry.visibility = View.VISIBLE
            } finally {
                avmTurnBusy = false
                views.avmTurn.isEnabled = apolloAvailable
            }
        }
    }
    private var apolloCapabilityJob: kotlinx.coroutines.Job? = null

    private fun refreshApolloCapability() {
        apolloCapabilityJob?.cancel()
        apolloCapabilityJob = lifecycleScope.launch {
            repeat(5) { attempt ->
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(listOf("su", "0", "apollo-capability"), 5)
                }
                val available = if (result.exitCode == 0 && !result.timedOut)
                    Regex("APOLLO_CAPABILITY available=(\\d+)").find(result.output)?.groupValues?.get(1)?.toIntOrNull() else null
                if (available != null) {
                    apolloAvailable = available == 1
                    binding.apolloTab.visibility = if (apolloAvailable) View.VISIBLE else View.GONE
                    binding.comfortPage.avmTurnCard.visibility = if (apolloAvailable) View.VISIBLE else View.GONE
                    if (apolloAvailable) updateAvmTurn("read")
                    if (!apolloAvailable && selectedTab == Tab.APOLLO) selectTab(Tab.SETTINGS)
                    return@launch
                }
                if (attempt < 4) delay(1500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshApolloCapability()
        com.abrar.voyahiran.feature.subscription.SubscriptionService.start(this)
        if(!(application as VoyahIranApplication).subscriptions.allowed) {
            startActivity(Intent(this,com.abrar.voyahiran.feature.subscription.SubscriptionActivity::class.java).putExtra("userLaunch",true))
            return
        }
        if (!permissionsEntry && projectNotice?.isShowing != true) {
            projectNotice = ProjectNoticeDialog(this).also { it.show() }
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if(!(application as VoyahIranApplication).subscriptions.allowed) {
            if(event.action==android.view.MotionEvent.ACTION_DOWN) startActivity(Intent(this,com.abrar.voyahiran.feature.subscription.SubscriptionActivity::class.java).putExtra("userLaunch",true))
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onPause() {
        apolloCapabilityJob?.cancel()
        projectNotice?.dismiss()
        projectNotice = null
        super.onPause()
    }
    private val localProfile: com.abrar.voyahiran.feature.settings.presentation.LocalProfileViewModel by viewModels()
    private val reboot: com.abrar.voyahiran.feature.settings.presentation.RebootViewModel by viewModels()
    private val permissions: com.abrar.voyahiran.feature.permissions.PermissionsViewModel by viewModels()
    private val apollo: com.abrar.voyahiran.feature.apollo.presentation.ApolloViewModel by viewModels()
    private val keyboard: com.abrar.voyahiran.feature.keyboard.presentation.KeyboardViewModel by viewModels()
    private val rearCamera: com.abrar.voyahiran.feature.rearcamera.presentation.RearCameraViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private val model: SettingsViewModel by viewModels {
        SettingsViewModel.Factory((application as VoyahIranApplication).timeZoneRepository)
    }
    private lateinit var diagnostics: com.abrar.voyahiran.feature.diagnostics.DiagnosticsPageController
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
        permissionsEntry = savedInstanceState?.getBoolean("permissionsEntry") == true ||
            intent.hasExtra("com.abrar.voyahiran.OPEN_PERMISSIONS_PACKAGE")
        com.abrar.voyahiran.core.logging.AppLog.info("App", "start version=${BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT} uid=${android.os.Process.myUid()} display=${window.decorView.display?.displayId}")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.abrar.voyahiran.feature.navigation.DisplayMediaMapController(this, binding.displayMediaMapPage).bind()
        binding.displayMediaMapTab.setOnClickListener { selectTab(Tab.DISPLAY_MEDIA_MAP) }
        diagnostics = com.abrar.voyahiran.feature.diagnostics.DiagnosticsPageController(this, binding.diagnosticsPage)
        // Diagnostics is temporarily unavailable from the navigation menu.
        fun updateLogging(operation: String) {
            val checkbox = binding.settingsPage.displaySpeed
            checkbox.isEnabled = false
            lifecycleScope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.abrar.voyahiran.core.shell.ProcessCommandRunner().run(
                        listOf("su", "0", "system-logging", operation), 15)
                }
                if (result.exitCode == 0 && result.output.contains("LOGGING_CONTROL")) {
                    checkbox.isChecked = result.output.contains("enabled=true")
                } else {
                    if (operation != "status") checkbox.isChecked = !checkbox.isChecked
                    android.widget.Toast.makeText(this@MainActivity, "تغییر وضعیت ثبت گزارش‌ها تأیید نشد", android.widget.Toast.LENGTH_LONG).show()
                }
                checkbox.isEnabled = true
            }
        }
        binding.settingsPage.displaySpeed.setOnClickListener {
            updateLogging(if (binding.settingsPage.displaySpeed.isChecked) "enable" else "disable")
        }
        updateLogging("status")
        binding.settingsPage.localProfileCard.visibility = if (com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest) View.VISIBLE else View.GONE
        binding.settingsPage.localProfileApply.setOnClickListener {
            localProfile.run(if (localProfile.state.value.enabled == null) "status"
                else if (binding.settingsPage.localProfileApply.isChecked) "apply" else "restore")
        }
        if (com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest) localProfile.run("status")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                localProfile.state.collect { state ->
                    with(binding.settingsPage) {
                        localProfileApply.isEnabled = !state.busy
                        localProfileApply.isChecked = state.enabled == true
                        localProfileProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
                        localProfileStatus.text = state.text
                        localProfileStatus.visibility = if (state.text.isBlank()) View.GONE else View.VISIBLE
                    }
                }
            }
        }

        binding.settingsPage.rebootDevice.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.reboot_label)
                .setMessage(R.string.reboot_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reboot_label) { _, _ -> reboot.reboot() }
                .show()
        }
        com.abrar.voyahiran.feature.voice.VoicePageController(this, binding.voicePage).bind()
        binding.voiceTab.setOnClickListener { selectTab(Tab.VOICE) }
        binding.settingsTab.setOnClickListener { selectTab(Tab.SETTINGS) }
        binding.comfortTab.setOnClickListener { selectTab(Tab.COMFORT) }
        binding.apolloTab.visibility = View.GONE
        binding.apolloTab.setOnClickListener { if (apolloAvailable) { selectTab(Tab.APOLLO); apollo.refresh() } }
        apolloControls().forEach { (box, feature, _) ->
            box.setOnClickListener { apollo.setFeature(feature, box.isChecked) }
        }
        binding.apolloPage.subscription.setOnClickListener { apollo.setEnabled(binding.apolloPage.subscription.isChecked) }
        binding.apolloPage.refresh.setOnClickListener { apollo.refresh() }
        binding.apolloPage.intelligentSpeed.setOnClickListener {
            apollo.setExperiment(com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED, if (binding.apolloPage.intelligentSpeed.isChecked) 1 else 2)
        }
        binding.apolloPage.speedWarningEnabled.setOnClickListener {
            val enabled = binding.apolloPage.speedWarningEnabled.isChecked
            apollo.setExperiment(com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED_WARNING, if (enabled) 2 else 1)
        }
        binding.apolloPage.speedWarningExact.setOnClickListener {
            apollo.setExperiment(com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED_OFFSET, 1)
        }
        binding.apolloPage.speedWarningPercent.setOnClickListener {
            apollo.setExperiment(com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED_OFFSET, 2)
        }
        binding.permissionsTab.setOnClickListener { selectTab(Tab.PERMISSIONS); permissions.refresh() }
        binding.permissionsPage.forceStop.setOnClickListener { permissions.forceStop() }
        binding.permissionsPage.uninstall.setOnClickListener {
            val state = permissions.state.value
            val app = state.apps.firstOrNull { it.packageName == state.selected }
            if (app != null && !state.busy) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.uninstall_app)
                    .setMessage(getString(R.string.uninstall_confirm, app.label))
                    .setNegativeButton(R.string.uninstall_cancel, null)
                    .setPositiveButton(R.string.uninstall_app) { _, _ -> permissions.uninstall(app.packageName) }
                    .show()
            }
        }
        binding.permissionsPage.refresh.setOnClickListener { permissions.refresh() }
        binding.permissionsPage.apps.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position) as? com.abrar.voyahiran.feature.permissions.InstalledApp ?: return
                permissions.select(item.packageName)
            }
        }
        binding.aboutTab.setOnClickListener { selectTab(Tab.ABOUT) }
        val appDisplay = (application as VoyahIranApplication).appDisplay
        binding.displayMediaMapPage.appDisplayCorrection.setOnClickListener {
            val desired = binding.displayMediaMapPage.appDisplayCorrection.isChecked
            binding.displayMediaMapPage.appDisplayCorrection.isChecked = appDisplay.state.value.enabled
            appDisplay.setEnabled(desired)
        }
        binding.displayMediaMapPage.appDisplayRetry.setOnClickListener { appDisplay.refresh() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appDisplay.state.collect { state ->
                    binding.displayMediaMapPage.appDisplayCorrection.isChecked = state.enabled
                    binding.displayMediaMapPage.appDisplayCorrection.isEnabled = !state.busy && com.abrar.voyahiran.core.compat.VehiclePlatform.displayFrameworkHooks
                    binding.displayMediaMapPage.appDisplayProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
                    binding.displayMediaMapPage.appDisplayError.visibility = if (state.failed || !com.abrar.voyahiran.core.compat.VehiclePlatform.displayFrameworkHooks) View.VISIBLE else View.GONE
                    if (!com.abrar.voyahiran.core.compat.VehiclePlatform.displayFrameworkHooks) binding.displayMediaMapPage.appDisplayError.text = com.abrar.voyahiran.core.compat.VehiclePlatform.frameworkUnavailable
                    binding.displayMediaMapPage.appDisplayRetry.visibility = if (state.failed) View.VISIBLE else View.GONE
                }
            }
        }
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
        binding.displayMediaMapPage.mediaControls.isChecked = com.abrar.voyahiran.feature.media.MediaControl.enabled(this)
        binding.displayMediaMapPage.mediaControls.setOnClickListener {
            val wanted = binding.displayMediaMapPage.mediaControls.isChecked
            binding.displayMediaMapPage.mediaControls.isEnabled = false
            binding.displayMediaMapPage.mediaControlsError.visibility = View.GONE
            lifecycleScope.launch {
                try {
                    com.abrar.voyahiran.feature.media.MediaControl.save(this@MainActivity, wanted)
                    com.abrar.voyahiran.feature.media.MediaControl.provision(this@MainActivity)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    com.abrar.voyahiran.core.logging.AppLog.error("SteeringMedia", "configure failed", e)
                    com.abrar.voyahiran.feature.media.MediaControl.save(this@MainActivity, !wanted)
                    binding.displayMediaMapPage.mediaControls.isChecked = !wanted
                    binding.displayMediaMapPage.mediaControlsError.visibility = View.VISIBLE
                } finally { binding.displayMediaMapPage.mediaControls.isEnabled = true }
            }
        }
        binding.settingsPage.starBack.setOnClickListener {
            requestSteering(if (binding.settingsPage.starBack.isChecked) StarBackService.ENABLE else StarBackService.DISABLE)
        }


        binding.comfortPage.driveMemory.setOnClickListener {
            requestDrive(if (binding.comfortPage.driveMemory.isChecked) DriveMemoryService.ENABLE else DriveMemoryService.DISABLE)
        }
        binding.comfortPage.driveRetry.setOnClickListener { requestDrive(DriveMemoryService.RETRY) }
        binding.comfortPage.avmTurn.setOnClickListener { updateAvmTurn(if (binding.comfortPage.avmTurn.isChecked) "on" else "off") }
        binding.comfortPage.avmTurnRetry.setOnClickListener { updateAvmTurn("read") }
        binding.comfortPage.recoveryMemory.setOnClickListener {
            requestDrive(if (binding.comfortPage.recoveryMemory.isChecked) DriveMemoryService.RECOVERY_ENABLE else DriveMemoryService.RECOVERY_DISABLE)
        }
        binding.comfortPage.recoveryRetry.setOnClickListener { requestDrive(DriveMemoryService.RECOVERY_RETRY) }
        binding.aboutPage.version.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        selectTab(Tab.entries.firstOrNull { it != Tab.DIAGNOSTICS && it.name == savedInstanceState?.getString(TAB_KEY) } ?: Tab.SETTINGS)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    (application as VoyahIranApplication).subscriptions.vipState.collect {
                        renderApollo(apollo.state.value)
                        renderDrive(drive.state.value)
                        if (com.abrar.voyahiran.core.compat.VehiclePlatform.localProfileTest) localProfile.run("status")
                    }
                }
                // Opening the UI and an in-place subscription unlock share this path.
                launch {
                    (application as VoyahIranApplication).subscriptions.state
                        .map { it == com.abrar.voyahiran.feature.subscription.SubscriptionStatus.ACTIVE }
                        .distinctUntilChanged()
                        .collect { ready -> if (ready) onAppStartup() }
                }
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
                launch { keyboard.state.collect(::renderKeyboard) }
                launch { rearCamera.state.collect(::renderRearCamera) }
                launch { rearCamera.needsRebootPrompt.collect { show ->
                    if (show) {
                        rearCamera.acknowledgeRebootPrompt()
                        val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.rear_camera_reboot_title)
                            .setMessage(R.string.rear_camera_reboot_message)
                            .setPositiveButton(R.string.reboot_label) { _, _ -> reboot.restartAfterVehicleConfiguration() }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        permissionsEntry = intent.hasExtra("com.abrar.voyahiran.OPEN_PERMISSIONS_PACKAGE")
        openRequestedPermissions()
    }

    private fun openRequestedPermissions() {
        val key="com.abrar.voyahiran.OPEN_PERMISSIONS_PACKAGE"
        val pkg=intent?.getStringExtra(key) ?: return
        intent.removeExtra(key)
        if(!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(pkg))return
        permissionsEntry = true
        projectNotice?.dismiss()
        projectNotice = null
        selectTab(Tab.PERMISSIONS)
        // App selector and permission switches are now the first section of this page.
        binding.permissionsPage.root.post { binding.permissionsPage.root.scrollTo(0,0) }
        permissions.openApp(pkg)
    }

    override fun onStart() {
        super.onStart()
        openRequestedPermissions()
        com.abrar.voyahiran.feature.navigation.NeshanConfigStore.refresh(this)
        // This receiver only listens to protected system broadcasts, not app commands.
        registerReceiver(timeChanged, IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
    }

    /** Keep startup refreshes here; subscription activation uses this same method. */
    private fun onAppStartup() {
        if(!(application as VoyahIranApplication).subscriptions.allowed) return
        com.abrar.voyahiran.core.logging.AppLog.info("App", "startup refresh after runtime activation")
        model.refresh()
        keyboard.refresh()
        if (selectedTab == Tab.PERMISSIONS) permissions.refresh()
        if ((application as VoyahIranApplication).starBackup.let { it.desired || it.read() != null }) {
            StarBackService.request(this, StarBackService.RESUME)
        } else lifecycleScope.launch { steering.refresh() }
        if ((application as VoyahIranApplication).subscriptions.vipAllowed) {
            if (drive.state.value.anyEnabled) DriveMemoryService.request(this, DriveMemoryService.RESUME)
            else lifecycleScope.launch { drive.start() }
        }
    }

    override fun onStop() {
        unregisterReceiver(timeChanged)
        if (!drive.state.value.anyEnabled) lifecycleScope.launch { drive.detach() }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionsEntry", permissionsEntry)
        outState.putString(TAB_KEY, selectedTab.name)
        super.onSaveInstanceState(outState)
    }

    private fun selectTab(tab: Tab) {
        if (tab == Tab.APOLLO && !apolloAvailable) { selectTab(Tab.SETTINGS); return }
        if (tab == Tab.ABOUT) { selectTab(Tab.SETTINGS); return }
        com.abrar.voyahiran.core.logging.AppLog.info("UI", "tab=$tab")
        if (selectedTab == Tab.DIAGNOSTICS || tab == Tab.DIAGNOSTICS) diagnostics.reset()
        binding.displayMediaMapTab.isSelected = tab == Tab.DISPLAY_MEDIA_MAP
        binding.displayMediaMapPage.root.visibility = if (tab == Tab.DISPLAY_MEDIA_MAP) View.VISIBLE else View.GONE
        selectedTab = tab
        binding.diagnosticsTab.isSelected = tab == Tab.DIAGNOSTICS
        binding.diagnosticsPage.root.visibility = if (tab == Tab.DIAGNOSTICS) View.VISIBLE else View.GONE
        binding.voiceTab.isSelected = tab == Tab.VOICE
        binding.voicePage.root.visibility = if (tab == Tab.VOICE) View.VISIBLE else View.GONE
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
        rearCameraCard.visibility = if (!com.abrar.voyahiran.core.compat.VehiclePlatform.isH97a && state.snapshot?.dvrAvailable == true) View.VISIBLE else View.GONE
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

    // One mapping for labels, readback and commands; hidden rows cannot shift labels.
    private fun apolloControls() = with(binding.apolloPage) {
        listOf(
            Triple(laneChange, com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.LANE_CHANGE, R.string.apollo_lane_change),
            Triple(trafficLight, com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.TRAFFIC_LIGHT, R.string.apollo_traffic_light),
            Triple(lightChange, com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.LIGHT_CHANGE, R.string.apollo_light_change),
            Triple(signs, com.abrar.voyahiran.feature.apollo.domain.ApolloFeature.SIGNS, R.string.apollo_signs),
        )
    }

    private fun renderApollo(state: com.abrar.voyahiran.feature.apollo.presentation.ApolloUiState) = with(binding.apolloPage) {
        fun show(box: android.widget.CheckBox, title: Int, value: Boolean?) {
            box.isChecked = value == true
            box.text = if (value == null) getString(R.string.apollo_unknown, getString(title)) else getString(title)
        }
        subscription.text = getString(R.string.apollo_subscription)
        subscription.isChecked = com.abrar.voyahiran.feature.apollo.data.ApolloControl.checked(this@MainActivity)
        subscription.isEnabled = !state.busy
        state.error?.let { android.widget.Toast.makeText(this@MainActivity, it, android.widget.Toast.LENGTH_LONG).show() }
        apolloControls().forEach { (box, feature, title) ->
            show(box, title, state.report.features[feature])
            box.isEnabled = subscription.isChecked && !state.busy && state.report.features[feature] != null
            box.alpha = if (subscription.isChecked) 1f else 0.45f
        }
        experiments.visibility = View.GONE
        val speedState = state.report.experiments[com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED]
        show(intelligentSpeed, R.string.apollo_intelligent_speed, when (speedState) { 1 -> true; 2 -> false; else -> null })
        intelligentSpeed.isEnabled = subscription.isChecked && !state.busy && speedState in 1..2
        intelligentSpeed.alpha = if (subscription.isChecked && speedState in 1..2) 1f else 0.45f
        val warningState = state.report.experiments[com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED_WARNING]
        val warningEnabled = warningState == 2
        show(speedWarningEnabled, R.string.apollo_speed_warning, when (warningState) { 1 -> false; 2 -> true; else -> null })
        speedWarningEnabled.isEnabled = subscription.isChecked && !state.busy && warningState in 1..2
        speedWarningEnabled.alpha = if (subscription.isChecked && warningState in 1..2) 1f else 0.45f
        speedWarningModes.visibility = if (warningEnabled) View.VISIBLE else View.GONE
        val speedStyle = state.report.experiments[com.abrar.voyahiran.feature.apollo.domain.ApolloExperiment.SPEED_OFFSET]
        speedWarningExact.isChecked = speedStyle == 1
        speedWarningPercent.isChecked = speedStyle == 2
        listOf(speedWarningExact, speedWarningPercent).forEach {
            it.isEnabled = subscription.isChecked && warningEnabled && !state.busy && speedStyle in 1..2
            it.alpha = if (subscription.isChecked && speedStyle in 1..2) 1f else 0.45f
        }
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        refresh.isEnabled = !state.busy
    }

    private var permissionApps: List<com.abrar.voyahiran.feature.permissions.InstalledApp> = emptyList()
    private fun renderPermissions(state: com.abrar.voyahiran.feature.permissions.PermissionsState) = with(binding.permissionsPage) {
        apps.isEnabled = !state.busy
        if (permissionApps != state.apps) {
            permissionApps = state.apps
            apps.adapter = com.abrar.voyahiran.feature.permissions.InstalledAppAdapter(this@MainActivity, state.apps)
        }
        val position = state.apps.indexOfFirst { it.packageName == state.selected }
        if (position >= 0 && apps.selectedItemPosition != position) apps.setSelection(position)
        uninstall.isEnabled = !state.busy && state.selected != null
        forceStop.isEnabled = !state.busy && state.selected != null
        val sizeButtons = listOf(packageSizeTiny, packageSizeSmall, packageSizeNormal, packageSizeLarge, packageSizeHuge)
        val densities = listOf(120, 140, 160, 200, 240)
        val effectiveDensity = state.customDensity ?: state.defaultDensity
        packageSizeChoices.check(sizeButtons[densities.indexOf(effectiveDensity).coerceAtLeast(0)].id)
        packageSizeInherit.isChecked = state.customDensity == null
        packageSizeChoices.visibility = if (state.customDensity == null) View.GONE else View.VISIBLE
        packageSizeInherit.isEnabled = !state.busy && state.selected != null
        packageSizeInherit.setOnClickListener {
            permissions.setDensity(if (packageSizeInherit.isChecked) "inherit" else effectiveDensity.toString())
        }
        sizeButtons.forEachIndexed { index, button ->
            button.isEnabled = !state.busy && state.selected != null
            button.setOnClickListener { permissions.setDensity(densities[index].toString()) }
        }
        apps.isEnabled = !state.busy
        refresh.isEnabled = !state.busy
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        feedback.visibility = if (state.error || state.stoppedApp != null) View.VISIBLE else View.GONE
        feedback.setTextColor(getColor(if (state.error) R.color.error else R.color.accent))
        feedback.text = if (state.error) getString(R.string.permissions_error)
            else state.stoppedApp?.let { getString(R.string.force_stop_success, it) }.orEmpty()
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
    private enum class Tab { SETTINGS, DISPLAY_MEDIA_MAP, COMFORT, VOICE, APOLLO, PERMISSIONS, DIAGNOSTICS, ABOUT }
    companion object { private const val TAB_KEY = "selected_tab" }
}







