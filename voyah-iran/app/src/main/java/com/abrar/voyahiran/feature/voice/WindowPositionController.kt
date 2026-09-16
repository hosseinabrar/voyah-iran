package com.abrar.voyahiran.feature.voice

/** OEM percentage feedback, automatic travel and feedback-driven STOP. No time-to-percentage assumption. */
class WindowPositionController(
    private val read: (String) -> Int,
    private val write: (String, Int) -> Unit,
    private val log: (String) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: (Long) -> Unit = Thread::sleep
) {
    fun move(goals: List<RahaWrite>) {
        val started = now()
        data class Motion(val field: String, val target: Int, val direction: Int,
                          var position: Int, var changedAt: Long)
        fun position(field: String) = read(feedback.getValue(field)).also {
            check(it in 0..100) { "Invalid window feedback: $field=$it" }
        }
        // Read ALL positions before moving any window.
        val motions = goals.map { goal ->
            require(goal.value in 0..100)
            val current = position(goal.field)
            log("start field=${goal.field} current=$current target=${goal.value}")
            Motion(goal.field, goal.value, if (goal.value > current) 1 else -1, current, started)
        }.filter { kotlin.math.abs(it.target - it.position) > tolerance(it.target) }
        val active = linkedSetOf<String>()
        try {
            for (motion in motions) {
                active += motion.field // STOP even when the movement acknowledgment is ambiguous.
                // VehicleControlHelper.openTwoWindows/closeTwoWindows use 3/1.
                // A single manual request (4/2) does not sustain travel on the tested car.
                val command = if (motion.direction > 0) 3 else 1
                log("move field=${motion.field} command=$command target=${motion.target}")
                write(motion.field, command)
            }
            while (active.isNotEmpty()) {
                check(now() - started < 8000) { "Window movement exceeded 8 seconds" }
                sleep(60)
                for (motion in motions.filter { it.field in active }) {
                    val current = position(motion.field)
                    if (current != motion.position) {
                        log("feedback field=${motion.field} position=$current target=${motion.target} elapsedMs=${now()-started}")
                        check((current - motion.position) * motion.direction >= -3) { "Window reversed: ${motion.field}" }
                        motion.position = current
                        motion.changedAt = now()
                    }
                    val reached = (motion.target - current) * motion.direction <= tolerance(motion.target)
                    if (reached) {
                        write(motion.field, 5)
                        active -= motion.field
                        log("stop field=${motion.field} position=$current target=${motion.target} reason=target elapsedMs=${now()-started}")
                    } else {
                        check(now() - motion.changedAt < 1500) { "Window feedback stalled: ${motion.field} position=$current" }
                    }
                }
            }
        } finally {
            // On timeout, read failure or reversal, attempt STOP independently for every active window.
            for (field in active) {
                try { write(field, 5); log("stop field=$field reason=abort elapsedMs=${now()-started}") }
                catch (e: Exception) { log("STOP FAILED field=$field error=$e") }
            }
        }
        for (motion in motions) log("final field=${motion.field} position=${position(motion.field)} target=${motion.target}")
    }

    private fun tolerance(target: Int) = if (target == 0 || target == 100) 0 else 3

    companion object {
        val feedback = linkedMapOf(
            "DRIVER_WINDOW_CONTROL" to "BCM_FLWindowHorizontalSts",
            "PAS_WIDOW_CONTROL" to "BCM_FRWindowHorizontalSts",
            "LEFT_BACK_WINDOW_CONTROL" to "BCM_RLWindowHorizontalSts",
            "RIGHT_BACK_WINDOW_CONTROL" to "BCM_RRWindowHorizontalSts"
        )
        fun fields(target: String): List<String> {
            val all = feedback.keys.toList()
            return when (target) {
                "driver" -> listOf(all[0]); "passenger" -> listOf(all[1])
                "rearLeft" -> listOf(all[2]); "rearRight" -> listOf(all[3])
                "front" -> all.take(2); "rear" -> all.drop(2)
                "left" -> listOf(all[0], all[2]); "right" -> listOf(all[1], all[3])
                "all" -> all; else -> error("Unsupported window target: $target")
            }
        }
    }
}
