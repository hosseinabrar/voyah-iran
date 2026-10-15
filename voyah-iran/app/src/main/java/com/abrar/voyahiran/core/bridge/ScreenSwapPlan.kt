package com.abrar.voyahiran.core.bridge

internal object ScreenSwapPlan {
    data class Stack(val id: Int, val display: Int, val pkg: String, val activity: String,
        val type: Int, val mode: Int, val visible: Boolean, val userApp: Boolean)
    private fun ownActivity(s: Stack): String? = if (s.pkg == "com.abrar.voyahiran")
        s.activity.removePrefix("com.abrar.voyahiran").removePrefix(".") else null
    private val homes = setOf("feature.launcher.VoyahLauncherActivity", "feature.launcher.PassengerLauncherActivity")
    private val drawers = setOf("feature.launcher.LauncherAppsActivity", "feature.launcher.PassengerAppsActivity",
        "feature.launcher.LauncherDrawerActivity")
    fun home(s: Stack) = s.type == 2 || ownActivity(s) in homes ||
        (s.pkg == "com.qinggan.app.launcher" && s.activity == "com.qinggan.secondlauncher.activity.SecondMainActivity")
    // Drawers are fixed to their display too, but must never be selected as the
    // source Home to reveal after transferring an ordinary app away.
    private fun movable(s: Stack) = !home(s) && ownActivity(s) !in drawers && s.userApp && s.type == 1 && s.mode == 1
    // Excluded foreground apps are destinations, never transfer candidates.
    fun empty(stacks: List<Stack>) = (0..1).all { d ->
        stacks.firstOrNull { it.display == d && it.visible }?.let { !movable(it) } == true
    }
    fun moves(stacks: List<Stack>): List<Stack> {
        val top = (0..1).map { d -> stacks.firstOrNull { it.display == d && it.visible } ?: return emptyList() }
        return top.reversed().filter(::movable)
    }
    fun signature(moves: List<Stack>) = moves.joinToString("|") { "${it.id}:${it.display}:${it.pkg}:${it.activity}" }
}
