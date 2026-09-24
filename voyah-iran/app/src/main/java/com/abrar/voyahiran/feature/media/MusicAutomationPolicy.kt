package com.abrar.voyahiran.feature.media

/** Unknown/initial door readings are not an opening event. */
internal class MusicAutomationPolicy {
    private var previousDoor: Int? = null
    fun doorOpened(value: Int): Boolean {
        if (value !in 0..1) { previousDoor = null; return false }
        val opened = previousDoor == 0 && value == 1
        previousDoor = value
        return opened
    }
    fun resetDoor() { previousDoor = null }
}
