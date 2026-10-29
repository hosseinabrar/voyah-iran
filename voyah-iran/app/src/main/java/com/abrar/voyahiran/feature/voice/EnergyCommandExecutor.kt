package com.abrar.voyahiran.feature.voice

/** Pure command execution contract. No injection into the OEM UI process. */
interface EnergyCommandPort {
    fun modes(): Map<String, Int>
    fun read(field: String): Int
    fun validate(field: String, bundle: Boolean)
    fun write(field: String, value: Int, bundle: Boolean)
    fun confirm(field: String, value: Int)
}

class EnergyCommandExecutor(private val port: EnergyCommandPort) {
    /** Restore raw OEM value; never change reserve percentage or relabel modes. */
    fun restoreMode(value: Int) {
        require(value in 1..4 && value in port.modes().values) { "Unsupported saved energy mode" }
        port.validate("IVI_SOC_MODESET", true)
        val current = port.read("IVI_SOC_MODESET")
        require(current in 1..4) { "Energy mode feedback unavailable" }
        if (current != value) {
            port.write("IVI_SOC_MODESET", value, true)
            port.confirm("IVI_SOC_MODESET", value)
        }
    }
    fun execute(action: String, p: Map<String, Any>) {
        fun integer(key: String): Int {
            val n=(p[key] as? Number)?.toDouble() ?: error("Missing $key")
            require(n.isFinite() && n in 0.0..100.0 && n%1==0.0) { "Invalid $key" }
            return n.toInt()
        }
        when(action) {
            "SET_SCHEDULED_CHARGING" -> {
                require(p.keys == setOf("enabled") && p["enabled"] is Boolean)
                // OEM ChargeFragment/CommonSwitch: scheduled=2, immediate=1.
                // Never write appointment start/end times or stop-charge requests.
                val field = "HUM_CHARGEMODE"
                val value = if (p["enabled"] == true) 2 else 1
                port.validate(field, true)
                val current = port.read(field)
                require(current in 1..2) { "Charge mode feedback unavailable" }
                if (current != value) port.write(field, value, true)
                port.confirm(field, value)
            }
            "SET_ENERGY_STRATEGY" -> {
                require(p.keys==setOf("strategy") || p.keys==setOf("strategy","reservePercent"))
                val name=p["strategy"] as? String ?: error("Missing strategy")
                val value=port.modes()[name] ?: error("Unsupported energy strategy: $name")
                val reserve=if(p.containsKey("reservePercent")) {
                    require(name=="save")
                    EnergyStrategyPolicy.reserve(integer("reservePercent"))
                } else null
                // Resolve every method/enum before the first write.
                port.validate("IVI_SOC_MODESET",true)
                if(reserve!=null)port.validate("SREV_SOC_SET",false)
                val before=port.read("IVI_SOC_MODESET")
                require(before in 1..4) { "Energy mode feedback unavailable: $before" }
                if(before!=value) {
                    port.write("IVI_SOC_MODESET",value,true)
                    port.confirm("IVI_SOC_MODESET",value)
                }
                // A failed mode change must not modify the reserve of the previous mode.
                if(reserve!=null) {port.write("SREV_SOC_SET",reserve,false);port.confirm("SREV_SOC_SET",reserve)}
            }
            "SET_BATTERY_RESERVE_TARGET" -> {
                require(p.keys==setOf("percent"))
                val value=EnergyStrategyPolicy.reserve(integer("percent"))
                require(port.modes().isNotEmpty()) { "Energy menu unavailable" }
                require(port.read("IVI_SOC_MODESET")==4) { "Select OEM reserve mode first" }
                port.validate("SREV_SOC_SET",false)
                port.write("SREV_SOC_SET",value,false);port.confirm("SREV_SOC_SET",value)
            }
            else -> error("Unsupported direct energy action: $action")
        }
    }
}
