package com.abrar.voyahiran.feature.dow

/** Experimental trigger. 4 is an OEM fault, NOT a danger warning. Verify 3 on vehicle. */
object DowContract {
    const val EXPERIMENTAL_WARNING = 3
    const val STATE_ORDINAL = 0x386
    const val STATE_ID = 0x45f
    const val SWITCH_ORDINAL = 0x1dc
    const val SWITCH_ID = 0x2b6
    const val RED = 62 // OEM atm_color_62_01, #fa4550.
    fun warningAllowed(state: Int?, doorOnly: Boolean, driverDoor: Int?) =
        state == EXPERIMENTAL_WARNING && (!doorOnly || driverDoor == 1)
    /** 250 ms on at full brightness, 250 ms completely off. */
    fun level(elapsed: Long): Int {
        val t = elapsed.coerceAtLeast(0) % 500
        return if (t < 250) 10 else 0
    }
}
data class AmbientSnapshot(val power: Int, val music: Int, val drive: Int, val color: Int, val brightness: Int) {
    fun valid() = power in 1..2 && music in 1..2 && drive in 0..1 && color in 1..64 && brightness in 1..10
}
data class AmbientJournal(val snapshot: AmbientSnapshot, val pending: Boolean)
interface AmbientStore {
    fun read(): AmbientJournal?
    /** Must be durable before returning. Never overwrite the original during a warning. */
    fun write(value: AmbientJournal)
}
interface AmbientPort {
    fun snapshot(): AmbientSnapshot
    fun normalRed()
    fun level(value: Int)
    fun restore(snapshot: AmbientSnapshot)
    fun confirmed(snapshot: AmbientSnapshot): Boolean
}

/** Serialized by bridge execution lock. Cleanup is allowed even after entitlement expires. */
class DowAmbientEngine(private val port: AmbientPort, private val store: AmbientStore) {
    var journal = store.read(); private set
    var flashing = false; private set
    private var began = 0L
    private var lastLevel = -1
    private var nextSample = 0L
    private var restoreSent: Long? = null
    private var nextRestore = 0L
    private var restoreOnActivation = true
    private var previousAcc: Int? = null
    private var blockedWarning = false

    fun tick(now: Long, allowed: Boolean, acc: Int?, dow: Int?) {
        if (acc == 0) restoreOnActivation = true
        if (acc == 2 && previousAcc == 0) restoreOnActivation = true
        previousAcc = acc
        val hazard = allowed && acc != null && dow == DowContract.EXPERIMENTAL_WARNING
        if (!hazard) blockedWarning = false
        if (flashing && !hazard) { flashing = false; lastLevel = -1 }
        if (acc == null) return // No writes on lost connection; durable pending recovery survives.
        if (journal?.pending == true && !flashing) {
            recover(now)
            return
        }
        if (!allowed) return
        if (restoreOnActivation && acc == 2 && !flashing) {
            restoreOnActivation = false
            journal?.let {
                save(it.copy(pending = true))
                recover(now)
                return
            }
        }
        if (hazard && !blockedWarning) {
            if (!flashing) {
                val before = port.snapshot()
                check(before.valid()) { "Ambient snapshot unavailable" }
                save(AmbientJournal(before, true)) // Fsync before any physical write.
                try {
                    port.normalRed()
                    began = -1; flashing = true; lastLevel = -1
                    return // Begin timing on the next tick, after slow OEM setup finishes.
                } catch (e: Exception) { blockedWarning = true; throw e }
            }
            if (began < 0) began = now
            val level = DowContract.level(now - began)
            if (level != lastLevel) {
                try { port.level(level); lastLevel = level }
                catch (e: Exception) { flashing = false; blockedWarning = true; throw e }
            }
        } else if (acc == 2 && now >= nextSample) {
            nextSample = now + 2000
            val current = port.snapshot()
            check(current.valid()) { "Ambient snapshot unavailable" }
            val saved = AmbientJournal(current, false)
            if (journal != saved) save(saved)
        }
    }
    private fun recover(now: Long) {
        val saved = journal ?: return
        if (restoreSent == null && now >= nextRestore) {
            // Bounded retries, never resume red while recovery is unconfirmed.
            nextRestore = now + 5000
            port.restore(saved.snapshot)
            restoreSent = now
        }
        val sent = restoreSent ?: return
        if (port.confirmed(saved.snapshot)) {
            save(saved.copy(pending = false))
            restoreSent = null; nextSample = now + 2000
        } else if (now - sent >= 2000) restoreSent = null
    }
    fun connectionLost() {
        flashing = false; lastLevel = -1; previousAcc = null
        restoreSent = null; restoreOnActivation = true
    }
    private fun save(value: AmbientJournal) { store.write(value); journal = value }
}
