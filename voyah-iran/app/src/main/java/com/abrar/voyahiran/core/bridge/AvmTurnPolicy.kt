package com.abrar.voyahiran.core.bridge

/** Serialized by the AVM adapter. No CAN writes and no guessed touch coordinates here. */
internal class AvmTurnPolicy {
    enum class Side { LEFT, RIGHT, FRONT }
    enum class Visibility { UNKNOWN, HIDDEN, USER, REVERSE }
    sealed interface Action {
        data class Open(val side: Side) : Action
        data class Select(val side: Side) : Action
        data object Close : Action
    }
    data class Input(
        val enabled: Boolean, val subscriptionActive: Boolean,
        val reverse: Boolean?, val speedKmh: Int?,
        // OEM switch states, not the blinking lamp states. null means unavailable.
        val left: Boolean?, val right: Boolean?, val visibility: Visibility,
        val pdc: Boolean? = false,
    )

    private var owned = false
    private var selected: Side? = null
    private var openingAt: Long? = null
    private var closeAt: Long? = null
    private var suppressed = false

    /** Every AVM visibility callback must be delivered, including hide followed by manual reopen. */
    fun visibilityChanged(visibility: Visibility) {
        if (visibility == Visibility.HIDDEN || visibility == Visibility.REVERSE || visibility == Visibility.UNKNOWN) {
            if (owned || openingAt != null) suppressed = true
            abandon()
        }
    }

    /** Called only for a successful open request; ownership still requires a USER visibility ack. */
    fun openResult(success: Boolean, now: Long) {
        if (success) openingAt = now else { suppressed = true; abandon() }
    }

    fun selectResult(success: Boolean) {
        // Do not keep fighting an unsupported layout or a user interaction.
        if (!success) suppressed = true
    }

    fun selectDeferred() { selected = null }

    fun step(input: Input, now: Long): Action? {
        val off = input.left == false && input.right == false
        if (off) suppressed = false
        val side = when {
            input.left == true && input.right == false -> Side.LEFT
            input.right == true && input.left == false -> Side.RIGHT
            else -> null // Hazards and unknown signals do not select an arbitrary side.
        }
        if (input.reverse != false || input.visibility == Visibility.REVERSE) {
            if (owned || openingAt != null) suppressed = true
            abandon()
            return null
        }
        // OEM can force-hide AVM at the speed threshold before delivering its callback.
        // Relinquish this session immediately; never race that hide with a front/close request.
        if (input.speedKmh != null && input.speedKmh >= 25 && (owned || openingAt != null)) {
            suppressed = !off
            abandon()
            return null
        }
        if (owned && input.visibility != Visibility.USER) {
            suppressed = !off
            abandon()
            return null
        }
        openingAt?.let { started ->
            if (input.visibility == Visibility.USER) {
                openingAt = null
                owned = true
            } else if (now - started >= 2_000) {
                suppressed = !off
                abandon()
                return null
            } else return null
        }
        // PDC owns camera presentation while active. Unknown is equally non-actionable.
        // Keep our ownership only while the same AVM session remains visible, but restart
        // the full close delay after PDC releases it.
        if (input.pdc != false) {
            closeAt = null
            return null
        }
        if (owned) {
            if (!input.enabled || !input.subscriptionActive) {
                abandon()
                return Action.Close
            }
            if (off) {
                val deadline = closeAt ?: (now + 3_000).also {
                    closeAt = it
                    selected = Side.FRONT
                    return Action.Select(Side.FRONT)
                }
                if (now >= deadline) { abandon(); return Action.Close }
            } else {
                closeAt = null
                if (side != null && input.speedKmh != null && input.speedKmh in 0..24 && selected != side) {
                    selected = side
                    return Action.Select(side)
                }
            }
            return null
        }
        if (!suppressed && input.enabled && input.subscriptionActive && side != null &&
            input.speedKmh != null && input.speedKmh in 0..24 && input.visibility == Visibility.HIDDEN) {
            // Adapter must synchronously call openResult before the next step.
            selected = null
            return Action.Open(side)
        }
        return null
    }

    private fun abandon() { owned = false; selected = null; openingAt = null; closeAt = null }
}
