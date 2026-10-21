package com.abrar.voyahiran.feature.navigation

/** Route recovery remains owned by the existing back-to-route gesture. */
internal object NeshanCardDrivingPolicy {
    enum class Action { ROUTE, FOLLOW, ALREADY_FOLLOWING, ENTER_DRIVING }
    fun action(routing: Boolean, driving: Boolean, followVisible: Boolean): Action = when {
        routing -> Action.ROUTE
        followVisible -> Action.FOLLOW
        driving -> Action.ALREADY_FOLLOWING
        else -> Action.ENTER_DRIVING
    }
}
