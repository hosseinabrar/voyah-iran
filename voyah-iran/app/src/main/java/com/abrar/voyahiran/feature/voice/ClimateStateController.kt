package com.abrar.voyahiran.feature.voice

/** H97C key pulses. A pulse is never repeated until a distinct feedback transition arrives. */
class ClimateStateController(
    private val read: (String) -> Int,
    private val pulse: (String) -> Unit,
    private val log: (String) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val manualFan: (Int) -> Unit = { error("Manual fan writer unavailable") }
) {
    fun set(field: String, desired: Int) {
        val getter = feedback.getValue(field)
        val states = if (field == "AC_RECIRC_AIR") 0..2 else 0..1
        require(desired in states)
        fun sample() = read(getter).also { check(it in states) { "Invalid $field feedback=$it" } }
        var current = sample()
        if (current == desired) { log("$field already=$desired; no pulse"); return }
        if (field == "AC_AUTO" && desired == 0) {
            // OEM voice cannot toggle AUTO off. Select manual blower control,
            // preserving current speed: feedback 0..7 maps to wire value 1..8.
            val speed=read("getAirWindSpeed")
            check(speed in 0..7) { "Invalid fan feedback=$speed; AUTO unchanged" }
            manualFan(speed+1)
            val deadline=now()+2000
            do {
                sleep(100)
                current=sample()
                if(current==0)break
            } while(now()<deadline)
            log("AC_AUTO manualFan=${speed+1} target=0 actual=$current")
            check(current==0) { "AC_AUTO manual fan exit not confirmed; not retried" }
            return
        }
        // Recirculation cycles across internal=0, external=1, auto=2.
        val limit = if (field == "AC_RECIRC_AIR") 2 else 1
        repeat(limit) {
            val before = current
            pulse(field)
            val deadline = now() + 2000
            do {
                sleep(100)
                current = sample()
                if (current != before) break
            } while (now() < deadline)
            log("$field pulse=${it+1} before=$before target=$desired actual=$current")
            check(current != before) { "$field feedback unchanged; toggle not retried" }
            if (current == desired) return
        }
        error("$field target=$desired actual=$current")
    }
    companion object {
        val feedback = mapOf(
            "AC_POWER_SWITCH" to "getAirSWStatus", "AC_SWITCH" to "getAirACStatus",
            "AC_AUTO" to "getAirMode", "AC_DUAL" to "getAirDUALStatus",
            "AC_FRONT_DEFROST_SWITCH" to "getAirFrontWindowDefogger",
            "AC_RECIRC_AIR" to "getAirCirculationMode"
        )
    }
}
