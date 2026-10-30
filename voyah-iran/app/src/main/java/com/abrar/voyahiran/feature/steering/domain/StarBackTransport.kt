package com.abrar.voyahiran.feature.steering.domain

/** Synthesized Back bypasses the OEM passenger-off key whitelist, without altering that whitelist. */
object StarBackTransport {
    fun directCenter(sdk: Int, event: RoutedKey): Boolean = (sdk == 28 || sdk == 30) &&
        event.code == SteeringKeys.BACK && event.action == StarEventPolicy.SHORT &&
        event.display == StarEventPolicy.CENTER_DISPLAY && event.flags == 0 && event.payload == null

    fun replayTargets(sdk: Int, previous: Int): List<Int> =
        (listOf(previous) + if (sdk == 28 || sdk == 30) emptyList() else listOf(SteeringKeys.BACK))
            .filter { it != SteeringKeys.STAR }.distinct()

    /** Release even if delivery of DOWN is rejected or its result is uncertain. Never replay DOWN. */
    fun press(inject: (action: Int) -> Boolean): Boolean {
        val down: Boolean
        var up = false
        try { down = inject(0) } finally { up = inject(1) }
        return down && up
    }
}
