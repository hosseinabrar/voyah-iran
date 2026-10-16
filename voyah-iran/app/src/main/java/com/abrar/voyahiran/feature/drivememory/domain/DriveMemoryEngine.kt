package com.abrar.voyahiran.feature.drivememory.domain

data class DriveMemory(val enabled: Boolean = false, val mode: Int? = null, val boot: String = "", val pending: Boolean = false)
interface DriveMemoryStore {
    fun read(): DriveMemory
    fun write(value: DriveMemory)
}
data class VehicleSample(val mode: Int? = null, val acc: Int? = null, val ready: Int? = null, val speed: Int? = null, val recovery: Int? = null, val aeb: Int? = null) {
    // H97c onAccStatus stores 2 for power-on and 0 for power-off (not boolean 1).
    val powered: Boolean get() = acc == 2
    val off: Boolean get() = acc == 0
}
/** All three comfort memories require VIP; saved preferences survive entitlement changes. */
data class MemoryAccess(val allowed: Boolean, val vipAllowed: Boolean) {
    fun permits() = allowed && vipAllowed
    fun active(aeb: Boolean, drive: Boolean, recovery: Boolean) =
        permits() && (aeb || drive || recovery)
}
enum class DrivePhase { OFF, WAITING, MONITORING, RESTORING, FAILED }
fun validDriveMode(mode: Int?) = mode != null && mode in 1..6

/** Serialized by the controller. A startup value can never overwrite a pending restore target. */
class DriveMemoryEngine(private val store: DriveMemoryStore, private val allowedValues: IntRange = 1..6) {
    private fun valid(mode: Int?) = mode != null && mode in allowedValues
    var memory = store.read(); private set
    var phase = if (memory.enabled) DrivePhase.WAITING else DrivePhase.OFF; private set
    private var sentAt: Long? = null
    private var candidate: Int? = null
    private var candidateAt = 0L
    private var wasOff = false

    fun start(boot: String) {
        require(boot.isNotBlank())
        // A process may have missed ACC-off while Android stayed alive; reconnect is also uncertain.
        if (memory.enabled) save(memory.copy(boot = boot, pending = true))
        phase = if (memory.enabled) DrivePhase.WAITING else DrivePhase.OFF
        sentAt = null
    }

    fun enable(sample: VehicleSample, boot: String) {
        // Saving an observed value issues no vehicle command and does not require READY.
        check(valid(sample.mode)) { "Vehicle mode is unavailable" }
        save(DriveMemory(true, sample.mode, boot, !sample.powered))
        phase = if (sample.powered) DrivePhase.MONITORING else DrivePhase.WAITING
        sentAt = null; candidate = null; wasOff = false
    }

    fun disable() {
        save(memory.copy(enabled = false, pending = false))
        phase = DrivePhase.OFF; candidate = null; sentAt = null
    }

    /** Returns one OEM request per power cycle; confirmation comes from observed CAN state. */
    fun observe(sample: VehicleSample, now: Long, modeObserved: Boolean): Int? {
        if (!memory.enabled) return null
        if (sample.off) {
            if (!memory.pending) save(memory.copy(pending = true))
            wasOff = true; sentAt = null; candidate = null; phase = DrivePhase.WAITING
            return null
        }
        if (!sample.powered || !valid(sample.mode)) { phase = DrivePhase.WAITING; return null }
        if (wasOff) { wasOff = false; phase = DrivePhase.WAITING }
        if (memory.pending) {
            if (!valid(memory.mode)) { phase = DrivePhase.FAILED; return null }
            if (phase == DrivePhase.FAILED) return null
            if (sample.mode == memory.mode && modeObserved) {
                save(memory.copy(pending = false))
                phase = DrivePhase.MONITORING; sentAt = null
                return null
            }
            if (sentAt != null) {
                phase = if (now - sentAt!! >= 10_000) DrivePhase.FAILED else DrivePhase.RESTORING
                return null
            }
            phase = DrivePhase.WAITING
            if (!modeObserved) return null
            // ACC-on is sufficient; both mode and recovery can be restored before READY.
            if (sample.speed != 0 || !valid(memory.mode)) return null
            sentAt = now; phase = DrivePhase.RESTORING
            return memory.mode
        }
        phase = DrivePhase.MONITORING
        if (modeObserved && sample.mode == memory.mode) candidate = null
        if (modeObserved && sample.mode != memory.mode && sample.mode != candidate) {
            candidate = sample.mode; candidateAt = now
        }
        if (candidate != null && sample.mode == candidate && now - candidateAt >= 400) {
            save(memory.copy(mode = candidate)); candidate = null
        }
        return null
    }

    fun requestFailed() { phase = DrivePhase.FAILED }
    fun retry() { if (memory.pending) { sentAt = null; phase = DrivePhase.WAITING } }
    private fun save(value: DriveMemory) {
        if (value != memory) { store.write(value); memory = value }
    }
}
