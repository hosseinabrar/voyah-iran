package com.abrar.voyahiran.feature.display

internal class ScreenSwapPress {
    private var captured: Boolean? = null
    @Synchronized fun reset() { captured = null }
    @Synchronized fun route(action: Int, eligible: Boolean): Pair<Boolean, Boolean> {
        if (action == 4096) captured = eligible
        val consume = captured ?: eligible
        val execute = consume && action == 65536
        // Keep ownership through UP until the separate short-click callback arrives.
        if (action == 65536 || action == 131072) captured = null
        return consume to execute
    }
}
