package com.abrar.voyahiran.feature.drivememory.presentation

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.feature.drivememory.data.*
import com.abrar.voyahiran.feature.drivememory.domain.*
import com.abrar.voyahiran.feature.recoverymemory.data.RecoveryLevelWriter
import com.abrar.voyahiran.feature.recoverymemory.domain.RecoveryRestoreOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DriveMemoryUiState(
    val enabled: Boolean = false, val current: Int? = null, val saved: Int? = null,
    val phase: DrivePhase = DrivePhase.OFF, val busy: Boolean = false, val error: Boolean = false,
    val recovery: RecoveryMemoryUiState = RecoveryMemoryUiState(),
) { val anyEnabled: Boolean get() = enabled || recovery.enabled }

data class RecoveryMemoryUiState(val enabled: Boolean = false, val current: Int? = null,
    val saved: Int? = null, val phase: DrivePhase = DrivePhase.OFF, val error: Boolean = false)

class DriveMemoryController(context: Context, store: DriveMemoryStore, recoveryStore: DriveMemoryStore) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val engine = DriveMemoryEngine(store)
    private val recoveryEngine = DriveMemoryEngine(recoveryStore, 2..4)
    private val recoveryWriter = RecoveryLevelWriter(ProcessCommandRunner())
    private val restoreOrder = RecoveryRestoreOrder()
    private val events = Channel<CanSignal>(Channel.UNLIMITED)
    private val gateway = CanDriveGateway(context) { events.trySend(it) }
    private val writer = OemDriveModeWriter(ProcessCommandRunner())
    private var sample = VehicleSample()
    private var running = false
    private var boot = ""
    private val mutable = MutableStateFlow(DriveMemoryUiState(enabled = engine.memory.enabled, saved = engine.memory.mode,
        recovery = RecoveryMemoryUiState(enabled = recoveryEngine.memory.enabled, saved = recoveryEngine.memory.mode)))
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            for (event in events) mutex.withLock {
                if (running) protect {
                    if (event.field == CanField.DISCONNECTED) { engine.start(boot); recoveryEngine.start(boot) }
                    sample = when (event.field) {
                        CanField.MODE -> sample.copy(mode = event.value)
                        CanField.RECOVERY -> sample.copy(recovery = event.value)
                        CanField.ACC -> sample.copy(acc = event.value)
                        CanField.READY -> sample.copy(ready = event.value)
                        CanField.SPEED -> sample.copy(speed = event.value)
                        CanField.DISCONNECTED -> VehicleSample()
                    }
                    process(event.field == CanField.MODE, event.field == CanField.RECOVERY)
                }
            }
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true, error = false)
        if (!running) {
            boot = bootIdentity()
            engine.start(boot)
            recoveryEngine.start(boot)
            running = true
        }
        sample = readVehicle()
        process(true, true)
    } } }

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
        if (!recoveryEngine.memory.enabled) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun tick() = withContext(Dispatchers.IO) { mutex.withLock { protect {
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
        if (!engine.memory.enabled) { running = false; gateway.close() }
        publish()
    } } }

    suspend fun retryRecovery() = withContext(Dispatchers.IO) { mutex.withLock { protect {
        mutable.value = mutable.value.copy(busy = true)
        recoveryEngine.retry()
        sample = readVehicle(); process(true, true)
    } } }

    suspend fun detach() = withContext(Dispatchers.IO) { mutex.withLock {
        running = false; gateway.close()
    } }

    private fun process(modeObserved: Boolean, recoveryObserved: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val driveWasPending = engine.memory.pending
        val request = engine.observe(sample, now, modeObserved)
        publish()
        if (request != null) {
            com.abrar.voyahiran.core.logging.AppLog.info("DriveMemory", "apply mode=$request")
            try { writer.apply(request) }
            catch (error: Exception) {
                com.abrar.voyahiran.core.logging.AppLog.error("DriveMemory", "apply-failed", error)
                engine.requestFailed(); publish(); return
            }
        }
        if (restoreOrder.allow(engine.memory.enabled, driveWasPending,
                engine.phase == DrivePhase.MONITORING, sample.off, now)) {
            val level = recoveryEngine.observe(sample.copy(mode = sample.recovery), now, recoveryObserved)
            if (level != null) {
                com.abrar.voyahiran.core.logging.AppLog.info("Recovery", "apply level=$level")
                publish()
                try { recoveryWriter.apply(level) }
                catch (error: Exception) {
                    com.abrar.voyahiran.core.logging.AppLog.error("Recovery", "apply-failed", error)
                    recoveryEngine.requestFailed()
                }
            }
        }
        publish()
    }

    private fun publish(error: Boolean = false) {
        com.abrar.voyahiran.core.logging.AppLog.changed("DriveMemory", "enabled=${engine.memory.enabled} saved=${engine.memory.mode} current=${sample.mode} phase=${engine.phase} pending=${engine.memory.pending} acc=${sample.acc} ready=${sample.ready} speed=${sample.speed} error=$error")
        com.abrar.voyahiran.core.logging.AppLog.changed("Recovery", "enabled=${recoveryEngine.memory.enabled} saved=${recoveryEngine.memory.mode} current=${sample.recovery} phase=${recoveryEngine.phase} pending=${recoveryEngine.memory.pending} error=$error")
        mutable.value = DriveMemoryUiState(engine.memory.enabled, sample.mode?.takeIf { validDriveMode(it) },
            engine.memory.mode, engine.phase, error = error || engine.phase == DrivePhase.FAILED,
            recovery = RecoveryMemoryUiState(recoveryEngine.memory.enabled, sample.recovery?.takeIf { it in 2..4 },
                recoveryEngine.memory.mode, recoveryEngine.phase, error || recoveryEngine.phase == DrivePhase.FAILED))
    }

    private fun bootIdentity() = "boot:" + Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private suspend fun readVehicle(): VehicleSample = try { gateway.read() }
    catch (error: Exception) {
        sample = VehicleSample()
        if (engine.memory.enabled) engine.start(boot.ifBlank { bootIdentity() })
        if (recoveryEngine.memory.enabled) recoveryEngine.start(boot.ifBlank { bootIdentity() })
        throw error
    }

    private suspend fun protect(block: suspend () -> Unit): Boolean {
        return try { block(); true }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            com.abrar.voyahiran.core.logging.AppLog.error("DriveMemory", "operation-failed", error)
            publish(error = true)
            false
        }
    }
}
