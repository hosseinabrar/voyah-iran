package com.abrar.voyahiran.feature.voice

internal interface PassengerPowerPort {
    fun state(screen: Int): Int
    fun setOff(screen: Int, off: Boolean)
    fun pause(milliseconds: Long)
}
/** OEM power screen numbers are 1-based, unlike Android display IDs. */
internal class PassengerPowerController(private val port: PassengerPowerPort) {
    fun execute(enabled: Boolean) {
        val screen = 2
        val expected = if (enabled) 2 else 5 // AVN_STATUS_RUN_ON / DISPLAY_OFF
        val current = port.state(screen)
        check(current == 2 || current == 5) { "Passenger display power unavailable: $current" }
        if (current == expected) return
        port.setOff(screen, !enabled)
        repeat(30) {
            if (port.state(screen) == expected) return
            port.pause(100)
        }
        check(port.state(screen) == expected) { "Passenger display did not confirm power change" }
    }
}
