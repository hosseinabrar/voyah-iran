package com.abrar.voyahiran.feature.navigation

/** A command owns exactly one ordered pair of clicks, not every future Neshan screen. */
internal class NeshanRouteProgress(val startedAt: Long) {
    enum class Step { DRIVING, START, VERIFY, DONE }
    var step = Step.DRIVING; private set
    private var lastClick = startedAt
    fun ready(now: Long) = now - lastClick >= 800 && now - startedAt < 45_000
    fun clicked(now: Long) {
        step = when (step) { Step.DRIVING -> Step.START; Step.START -> Step.VERIFY; else -> step }
        lastClick = now
    }
    fun verify(guidanceVisible: Boolean, now: Long): Boolean {
        if (step == Step.VERIFY && ready(now) && guidanceVisible) step = Step.DONE
        return step == Step.DONE
    }
}
