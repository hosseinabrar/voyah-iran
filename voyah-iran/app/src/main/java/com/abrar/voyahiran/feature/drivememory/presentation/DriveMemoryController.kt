package com.abrar.voyahiran.feature.drivememory.presentation

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.*
import com.abrar.voyahiran.feature.drivememory.domain.*
import com.abrar.voyahiran.feature.recoverymemory.data.RecoveryLevelWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DriveMemoryUiState(
    val enabled: Boolean = false, val current: Int? = null, val saved: Int? = null,
    val phase: DrivePhase = DrivePhase.OFF, val busy: Boolean = false, val error: Boolean = false,
    val recovery: RecoveryMemoryUiState = RecoveryMemoryUiState(),
    val aeb: RecoveryMemoryUiState = RecoveryMemoryUiState(),
    val pedestrian: RecoveryMemoryUiState = RecoveryMemoryUiState(),
    val energyMode: RecoveryMemoryUiState = RecoveryMemoryUiState(),
    val dowEnabled: Boolean = false, val dowError: Boolean = false,
    val dowDoorOnly: Boolean = true,
    val shadeReadyEnabled: Boolean = false, val shadeReadyError: Boolean = false, val shadeOpening: Boolean = false,
    val shadeEnabled: Boolean = false, val shadeError: Boolean = false, val shadeClosing: Boolean = false,
) { val anyEnabled: Boolean get() = dowEnabled || enabled || recovery.enabled || aeb.enabled || pedestrian.enabled || energyMode.enabled || shadeEnabled || shadeReadyEnabled }

data class RecoveryMemoryUiState(val enabled: Boolean = false, val current: Int? = null,
    val saved: Int? = null, val phase: DrivePhase = DrivePhase.OFF, val error: Boolean = false)

class DriveMemoryController(context: Context, store: DriveMemoryStore, recoveryStore: DriveMemoryStore, aebStore: DriveMemoryStore, pedestrianStore: DriveMemoryStore, energyModeStore: DriveMemoryStore) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dowMutex = Mutex()
    private val shadeMutex = Mutex()
    private var dowRenewing = false
    private val engine = DriveMemoryEngine(store)
    private val recoveryEngine = DriveMemoryEngine(recoveryStore, 2..4)
    private val aebEngine = DriveMemoryEngine(aebStore, 1..2)
    private val pedestrianEngine = DriveMemoryEngine(pedestrianStore, 1..2)
    private val energyModeEngine = DriveMemoryEngine(energyModeStore, 1..4)
    private val pedestrianWriter = PedestrianMemoryWriter(ProcessCommandRunner())
    private val energyModeWriter = EnergyModeMemoryWriter(ProcessCommandRunner())
    private val aebWriter = AebMemoryWriter(ProcessCommandRunner())
    private val dowPreferences = this.context.getSharedPreferences("dow_ambient", Context.MODE_PRIVATE)
    private var dowEnabled = dowPreferences.getBoolean("enabled", false)
    private var dowDoorOnly = dowPreferences.getBoolean("door_only", true)
    private var dowError = false
    private var nextDowHeartbeat = 0L
    private fun dowCommand(enabled: Boolean, doorOnly: Boolean = dowDoorOnly) {
        val result = ProcessCommandRunner().run(listOf("su", "0", "dow-light",
            if (!enabled) "disable" else if (doorOnly) "enable" else "enable-always"), 3)
        check(!result.timedOut && result.exitCode == 0 && result.output.trim() == "DOW_ACCEPTED") { "DOW runtime unavailable" }
    }
    private fun renewDow() {
        val now = SystemClock.elapsedRealtime()
        if (!dowEnabled || dowRenewing || now < nextDowHeartbeat) return
        nextDowHeartbeat = now + 5000
        dowRenewing = true
        scope.launch {
            try {
                dowMutex.withLock {
                    val mode = mutex.withLock { if (running && dowEnabled && access.permits()) dowDoorOnly else null }
                    if (mode != null) {
                        val failed = runCatching {
                            dowCommand(true, mode)
                            val result = ProcessCommandRunner().run(listOf("su", "0", "dow-light", "status"), 3)
                            check(!result.timedOut && result.exitCode == 0 && !result.output.startsWith("error:"))
                        }.isFailure
                        mutex.withLock { dowError = failed; publish() }
                    }
                }
            } finally { mutex.withLock { dowRenewing = false } }
        }
    }
    suspend fun setDowEnabled(enabled: Boolean) = withContext(Dispatchers.IO) { dowMutex.withLock {
        val permitted = mutex.withLock { access.permits() }
        if (!permitted) return@withLock false
        try {
        if (enabled) { writer.checkAccess(); dowCommand(true) }
        else dowCommand(false)
        mutex.withLock {
        check(dowPreferences.edit().putBoolean("enabled", enabled).commit())
        dowEnabled = enabled; dowError = false; nextDowHeartbeat = 0
        if (enabled) running = true else {
            if (!hasActiveMemory) { running = false; gateway.close() }
        }
        publish()
        }
        true
        } catch (error: Exception) {
            mutex.withLock { dowError = true; publish() }
            com.abrar.voyahiran.core.logging.AppLog.error("DOW", "setting failed", error)
            false
        }
    } }
    private val shadePreferences = this.context.getSharedPreferences("sunshade_departure", Context.MODE_PRIVATE)
    suspend fun setDowDoorOnly(value: Boolean) = withContext(Dispatchers.IO) { mutex.withLock { protect {
        check(access.permits()) { "VIP inactive" }
        check(dowPreferences.edit().putBoolean("door_only", value).commit())
        dowDoorOnly = value; nextDowHeartbeat = 0
        renewDow(); publish()
    } } }
    private var shadeEnabled = shadePreferences.getBoolean("enabled", false)
    private var shadeError = false
    private var shadeClosing = false
    private val shadePolicy = SunshadeDeparturePolicy()
    private var shadeReadyEnabled = shadePreferences.getBoolean("open_on_ready", false)
    private var shadeReadyError = false
    private var shadeOpening = false
    private val shadeReadyPolicy = SunshadeReadyPolicy()
    private val shadeReadyWriter = SunshadeReadyWriter(ProcessCommandRunner())
    private val shadeWriter = SunshadeDepartureWriter(ProcessCommandRunner())
    private val subscriptions get() = (context as com.abrar.voyahiran.VoyahIranApplication).subscriptions
    private val access get() = MemoryAccess(subscriptions.allowed, subscriptions.vipAllowed)
    val hasActiveMemory: Boolean get() = access.permits() && (dowEnabled || shadeEnabled || shadeReadyEnabled || engine.memory.enabled || recoveryEngine.memory.enabled || aebEngine.memory.enabled || pedestrianEngine.memory.enabled || energyModeEngine.memory.enabled)
    private val recoveryWriter = RecoveryLevelWriter(ProcessCommandRunner())
    private val memoryEngines = mapOf(
        MemorySetting.DRIVE to engine, MemorySetting.RECOVERY to recoveryEngine,
        MemorySetting.ENERGY_MODE to energyModeEngine, MemorySetting.AEB to aebEngine,
        MemorySetting.PEDESTRIAN to pedestrianEngine
    )
    private val memoryInFlight = mutableSetOf<MemorySetting>()
    private var commandGeneration = 0L
    private var powerWasOff = false
    private val restoreSequence = MemoryRestoreSequence(memoryEngines) { com.abrar.voyahiran.core.logging.AppLog.changed("MemoryRestore", it) }
    private val events = Channel<CanSignal>(Channel.UNLIMITED)
    private val gateway = CanDriveGateway(context) { events.trySend(it) }
    private val writer = OemDriveModeWriter(ProcessCommandRunner())
    private var sample = VehicleSample()
    private var running = false
    private var restorationStarted = false
    private var boot = ""
    private val mutable = MutableStateFlow(DriveMemoryUiState(enabled = engine.memory.enabled, saved = engine.memory.mode,
        recovery = RecoveryMemoryUiState(enabled = recoveryEngine.memory.enabled, saved = recoveryEngine.memory.mode),
        aeb = RecoveryMemoryUiState(enabled = aebEngine.memory.enabled, saved = aebEngine.memory.mode),
        pedestrian = RecoveryMemoryUiState(enabled = pedestrianEngine.memory.enabled, saved = pedestrianEngine.memory.mode),
        energyMode = RecoveryMemoryUiState(enabled = energyModeEngine.memory.enabled, saved = energyModeEngine.memory.mode),
        dowEnabled = dowEnabled, dowDoorOnly = dowDoorOnly,
        shadeEnabled = shadeEnabled, shadeReadyEnabled = shadeReadyEnabled))
    val state = mutable.asStateFlow()
    private val completion = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val requestCompletion = completion.asStateFlow()
    fun trackRequest(id: String) { completion.update { it + (id to null) } }
    fun forgetRequest(id: String) { completion.update { it - id } }
    fun completeRequest(id: String, success: Boolean) {
        completion.update { if (id in it) it + (id to success) else it }
    }

    init {
        scope.launch {
            for (event in events) mutex.withLock {
                if (running) protect {
                    if (event.field == CanField.DISCONNECTED) { commandGeneration++; engine.start(boot); recoveryEngine.start(boot); aebEngine.start(boot); pedestrianEngine.start(boot); energyModeEngine.start(boot); shadePolicy.reset(); shadeReadyPolicy.reset() }
                    sample = when (event.field) {
                        CanField.PEDESTRIAN -> sample.copy(pedestrian = event.value)
                        CanField.ENERGY_MODE -> sample.copy(energyMode = event.value)
                        CanField.AEB -> sample.copy(aeb = event.value)
                        CanField.MODE -> sample.copy(mode = event.value)
                        CanField.RECOVERY -> sample.copy(recovery = event.value)
                        CanField.ACC -> sample.copy(acc = event.value)
                        CanField.READY -> sample.copy(ready = event.value)
                        CanField.SPEED -> sample.copy(speed = event.value)
                        CanField.DISCONNECTED -> VehicleSample()
                    }
                    process(event.field == CanField.MODE, event.field == CanField.RECOVERY, event.field == CanField.AEB, event.field == CanField.PEDESTRIAN, event.field == CanField.ENERGY_MODE)
                }
            }
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true, error = false)
        ensureStarted()
        renewDow()
        sample = readVehicle()
        process(true, true)
    } } }

    private fun ensureStarted() {
        if (!restorationStarted) {
            boot = bootIdentity()
            restoreSequence.reset()
            shadePolicy.reset(); shadeReadyPolicy.reset()
            engine.start(boot)
            recoveryEngine.start(boot)
            aebEngine.start(boot)
            pedestrianEngine.start(boot)
            energyModeEngine.start(boot)
            restorationStarted = true
        }
        running = true
    }

    suspend fun enable() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true, error = false)
        writer.checkAccess()
        if (boot.isBlank()) boot = bootIdentity()
        sample = readVehicle()
        engine.enable(sample, boot)
        running = true
        publish()
    } } }

    suspend fun disable() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true, error = false)
        engine.disable()
        if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun tick() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        // A failed first startup must not require opening Settings to arm the loop again.
        if (hasActiveMemory) ensureStarted()
        renewDow()
        if (running) { sample = readVehicle(); process(true, true) }
    } } }

    suspend fun retry() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true, error = false)
        engine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun enableRecovery() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        writer.checkAccess()
        if (boot.isBlank()) boot = bootIdentity()
        sample = readVehicle()
        recoveryEngine.enable(sample.copy(mode = sample.recovery), boot)
        running = true; publish()
    } } }

    suspend fun disableRecovery() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        recoveryEngine.disable()
        if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun retryRecovery() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        recoveryEngine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun enableAeb() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        writer.checkAccess()
        if (boot.isBlank()) boot = bootIdentity()
        sample = readVehicle()
        aebEngine.enable(sample.copy(mode = sample.aeb), boot)
        running = true; publish()
    } } }

    suspend fun disableAeb() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        aebEngine.disable()
        if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun retryAeb() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        aebEngine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun enablePedestrian() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        writer.checkAccess()
        if (boot.isBlank()) boot = bootIdentity()
        sample = readVehicle()
        pedestrianEngine.enable(sample.copy(mode = sample.pedestrian), boot)
        running = true; publish()
    } } }

    suspend fun disablePedestrian() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        pedestrianEngine.disable()
        if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun retryPedestrian() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        pedestrianEngine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun enableEnergyMode() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        writer.checkAccess()
        if (boot.isBlank()) boot = bootIdentity()
        sample = readVehicle()
        energyModeEngine.enable(sample.copy(mode = sample.energyMode), boot)
        running = true; publish()
    } } }

    suspend fun disableEnergyMode() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        energyModeEngine.disable()
        if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun retryEnergyMode() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        energyModeEngine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun setShadeEnabled(enabled: Boolean) = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        if (enabled) {
            writer.checkAccess()
            if (boot.isBlank()) boot = bootIdentity()
            sample = readVehicle()
        }
        check(shadePreferences.edit().putBoolean("enabled", enabled).commit()) { "Sunshade preference not saved" }
        shadeEnabled = enabled
        shadeError = false
        shadePolicy.reset(if (enabled) sample.ready else null)
        if (enabled) running = true
        else if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    private fun closeShadeOnDeparture() {
        if (!shadePolicy.observe(sample.ready, sample.speed, shadeEnabled, access.permits())) return
        launchShade(false)
    }

    // Only opposite commands to the same physical motor share a lane. Neither
    // command holds the state mutex while waiting for OEM feedback.
    private fun launchShade(open: Boolean) {
        val generation = commandGeneration
        scope.launch { shadeMutex.withLock {
            val permitted = mutex.withLock {
                val valid = generation == commandGeneration && running && access.permits() && sample.speed == 0 &&
                    if (open) shadeReadyEnabled && sample.acc == 2 && sample.ready == 1
                    else shadeEnabled && sample.ready == 0
                if (valid) {
                    if (open) { shadeOpening = true; shadeReadyError = false }
                    else { shadeClosing = true; shadeError = false }
                    publish()
                }
                valid
            }
            if (!permitted) return@withLock
        val power = context.getSystemService(android.os.PowerManager::class.java)
        val tag = if (open) "SunshadeReady" else "SunshadeDeparture"
        val wake = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "VoyahIran:$tag")
        var failed = false
        try {
            // Short CPU hold only; never an unbounded wake lock or a retry after shutdown.
            wake.acquire(12_000)
            if (open) shadeReadyWriter.open() else shadeWriter.close()
            com.abrar.voyahiran.core.logging.AppLog.info(tag, "feedback confirmed")
        } catch (error: Exception) {
            failed = true
            com.abrar.voyahiran.core.logging.AppLog.error(tag, "not confirmed; no replay", error)
        } finally {
            if (wake.isHeld) wake.release()
            mutex.withLock {
                if (open) { shadeOpening = false; shadeReadyError = failed }
                else { shadeClosing = false; shadeError = failed }
                publish()
            }
        }
        } }
    }

    suspend fun setShadeReadyEnabled(enabled: Boolean) = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        if (enabled) {
            writer.checkAccess()
            if (boot.isBlank()) boot = bootIdentity()
            sample = readVehicle()
        }
        check(shadePreferences.edit().putBoolean("open_on_ready", enabled).commit()) { "Sunshade preference not saved" }
        shadeReadyEnabled = enabled
        shadeReadyError = false
        shadeReadyPolicy.reset(if (enabled) sample.ready else null)
        if (enabled) running = true
        else if (!hasActiveMemory) { running = false; gateway.close() }
        publish()
    } } }

    private fun openShadeOnReady() {
        if (!shadeReadyPolicy.observe(sample.acc, sample.ready, sample.speed, shadeReadyEnabled, access.permits())) return
        launchShade(true)
    }

    suspend fun detach() = withContext(Dispatchers.IO) {
        mutex.withLock {
        nextDowHeartbeat = 0
        commandGeneration++
        running = false; restorationStarted = false; gateway.close(); shadePolicy.reset(); shadeReadyPolicy.reset()
        }
        dowMutex.withLock {
            if (mutex.withLock { !running }) runCatching { dowCommand(false) }
        }
    }

    private fun process(modeObserved: Boolean, recoveryObserved: Boolean, aebObserved: Boolean = true, pedestrianObserved: Boolean = true, energyModeObserved: Boolean = true) {
        if (!access.permits()) { shadePolicy.reset(); shadeReadyPolicy.reset(); return }
        if(sample.off && !powerWasOff) commandGeneration++
        powerWasOff = sample.off
        closeShadeOnDeparture()
        openShadeOnReady()
        val now = SystemClock.elapsedRealtime()
        val observed=buildSet {
            if(modeObserved)add(MemorySetting.DRIVE)
            if(recoveryObserved)add(MemorySetting.RECOVERY)
            if(energyModeObserved)add(MemorySetting.ENERGY_MODE)
            if(aebObserved)add(MemorySetting.AEB)
            if(pedestrianObserved)add(MemorySetting.PEDESTRIAN)
        }
        restoreSequence.process(sample,now,observed,memoryInFlight) { key,value ->
            publish()
            val generation = commandGeneration
            memoryInFlight.add(key)
            scope.launch {
            var failed = false
            try {
                val permitted = mutex.withLock {
                    val memory = memoryEngines.getValue(key).memory
                    generation == commandGeneration && running && access.permits() && sample.powered &&
                        memory.enabled && memory.pending && memory.mode == value
                }
                if (!permitted) return@launch
                when(key) {
                    MemorySetting.DRIVE -> writer.apply(value)
                    MemorySetting.RECOVERY -> recoveryWriter.apply(value)
                    MemorySetting.ENERGY_MODE -> energyModeWriter.apply(value)
                    MemorySetting.AEB -> aebWriter.apply(value)
                    MemorySetting.PEDESTRIAN -> pedestrianWriter.apply(value)
                }
            } catch(error:Exception) {
                failed = true
                com.abrar.voyahiran.core.logging.AppLog.error("MemoryRestore","apply failed step=$key target=$value; bounded automatic retry scheduled",error)
            } finally {
                mutex.withLock {
                    memoryInFlight.remove(key)
                    val target = memoryEngines.getValue(key)
                    if (failed && generation == commandGeneration && target.memory.pending && target.memory.mode == value)
                        target.requestFailed()
                    publish()
                }
            }
            }
        }
        publish()
    }

    private fun publish(error: Boolean = false) {
        com.abrar.voyahiran.core.logging.AppLog.changed("DriveMemory", "enabled=${engine.memory.enabled} saved=${engine.memory.mode} current=${sample.mode} phase=${engine.phase} pending=${engine.memory.pending} acc=${sample.acc} ready=${sample.ready} speed=${sample.speed} error=$error")
        com.abrar.voyahiran.core.logging.AppLog.changed("Recovery", "enabled=${recoveryEngine.memory.enabled} saved=${recoveryEngine.memory.mode} current=${sample.recovery} phase=${recoveryEngine.phase} pending=${recoveryEngine.memory.pending} error=$error")
        com.abrar.voyahiran.core.logging.AppLog.changed("AebMemory", "enabled=${aebEngine.memory.enabled} saved=${aebEngine.memory.mode} current=${sample.aeb} phase=${aebEngine.phase} pending=${aebEngine.memory.pending} error=$error")
        com.abrar.voyahiran.core.logging.AppLog.changed("PedestrianMemory", "enabled=${pedestrianEngine.memory.enabled} saved=${pedestrianEngine.memory.mode} current=${sample.pedestrian} phase=${pedestrianEngine.phase} pending=${pedestrianEngine.memory.pending} error=$error")
        com.abrar.voyahiran.core.logging.AppLog.changed("EnergyModeMemory", "enabled=${energyModeEngine.memory.enabled} saved=${energyModeEngine.memory.mode} current=${sample.energyMode} phase=${energyModeEngine.phase} pending=${energyModeEngine.memory.pending} error=$error")
        mutable.value = DriveMemoryUiState(engine.memory.enabled, sample.mode?.takeIf { validDriveMode(it) },
            engine.memory.mode, engine.phase, error = error || engine.phase == DrivePhase.FAILED,
            recovery = RecoveryMemoryUiState(recoveryEngine.memory.enabled, sample.recovery?.takeIf { it in 2..4 },
                recoveryEngine.memory.mode, recoveryEngine.phase, error || recoveryEngine.phase == DrivePhase.FAILED),
            aeb = RecoveryMemoryUiState(aebEngine.memory.enabled, sample.aeb?.takeIf { it in 1..2 },
                aebEngine.memory.mode, aebEngine.phase, error || aebEngine.phase == DrivePhase.FAILED),
            pedestrian = RecoveryMemoryUiState(pedestrianEngine.memory.enabled, sample.pedestrian?.takeIf { it in 1..2 },
                pedestrianEngine.memory.mode, pedestrianEngine.phase, error || pedestrianEngine.phase == DrivePhase.FAILED),
            energyMode = RecoveryMemoryUiState(energyModeEngine.memory.enabled, sample.energyMode?.takeIf { it in 1..4 },
                energyModeEngine.memory.mode, energyModeEngine.phase, error || energyModeEngine.phase == DrivePhase.FAILED),
            dowEnabled = dowEnabled, dowError = dowError, dowDoorOnly = dowDoorOnly,
            shadeEnabled = shadeEnabled, shadeError = shadeError || error, shadeClosing = shadeClosing,
            shadeReadyEnabled = shadeReadyEnabled, shadeReadyError = shadeReadyError || error, shadeOpening = shadeOpening)
    }

    private fun bootIdentity() = "boot:" + Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private suspend fun readVehicle(): VehicleSample = try {
        com.abrar.voyahiran.core.logging.AppLog.info("DriveMemory", "stage=read-vehicle begin")
        gateway.read().also { com.abrar.voyahiran.core.logging.AppLog.info("DriveMemory", "stage=read-vehicle end sample=$it") }
    }
    catch (error: Exception) {
        commandGeneration++
        sample = VehicleSample()
        shadePolicy.reset(); shadeReadyPolicy.reset()
        if (engine.memory.enabled) engine.start(boot.ifBlank { bootIdentity() })
        if (recoveryEngine.memory.enabled) recoveryEngine.start(boot.ifBlank { bootIdentity() })
        if (aebEngine.memory.enabled) aebEngine.start(boot.ifBlank { bootIdentity() })
        if (pedestrianEngine.memory.enabled) pedestrianEngine.start(boot.ifBlank { bootIdentity() })
        if (energyModeEngine.memory.enabled) energyModeEngine.start(boot.ifBlank { bootIdentity() })
        throw error
    }

    private suspend fun protect(block: suspend () -> Unit): Boolean {
        if (!access.permits()) { shadePolicy.reset(); shadeReadyPolicy.reset(); publish(); return false }
        return try { block(); true }
        catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            com.abrar.voyahiran.core.logging.AppLog.error("DriveMemory", "operation-timeout; request not replayed", error)
            publish(error = true)
            false
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("DriveMemory", "operation-failed", error)
            publish(error = true)
            false
        }
        finally {
            if (mutable.value.busy) {
                mutable.value = mutable.value.copy(busy = false)
                com.abrar.voyahiran.core.logging.AppLog.info("DriveMemory", "busy cleared in operation cleanup")
            }
        }
    }
}
