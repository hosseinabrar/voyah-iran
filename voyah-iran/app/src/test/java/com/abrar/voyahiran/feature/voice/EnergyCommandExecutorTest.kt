package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class EnergyCommandExecutorTest {
    @Test fun scheduleOnlyChangesModeAndPreservesStoredTimes() {
        for (enabled in listOf(true, false)) {
            val p = Port().apply { mode = if (enabled) 1 else 2 }
            EnergyCommandExecutor(p).execute("SET_SCHEDULED_CHARGING", mapOf("enabled" to enabled))
            val value = if (enabled) 2 else 1
            assertEquals(listOf("write:HUM_CHARGEMODE:$value:true", "confirm:HUM_CHARGEMODE:$value"), p.operations)
            val same = Port().apply { mode = value }
            EnergyCommandExecutor(same).execute("SET_SCHEDULED_CHARGING", mapOf("enabled" to enabled))
            assertEquals(listOf("confirm:HUM_CHARGEMODE:$value"), same.operations)
        }
    }
    @Test fun scheduleRejectsTimeParametersAndMissingFeedback() {
        for (params in listOf(mapOf("enabled" to true, "hour" to 3), mapOf("enabled" to 1), emptyMap())) {
            val p = Port()
            rejected { EnergyCommandExecutor(p).execute("SET_SCHEDULED_CHARGING", params) }
            assertTrue(p.operations.isEmpty())
        }
        val p = Port().apply { mode = 255 }
        rejected { EnergyCommandExecutor(p).execute("SET_SCHEDULED_CHARGING", mapOf("enabled" to true)) }
        assertTrue(p.operations.isEmpty())
        val failed = Port().apply { mode = 1; reject = true }
        rejected { EnergyCommandExecutor(failed).execute("SET_SCHEDULED_CHARGING", mapOf("enabled" to true)) }
    }
    private class Port(val supported:Map<String,Int> = EnergyStrategyPolicy.modes(true,false,true)) : EnergyCommandPort {
        var mode=2
        var reject=false
        var missingReserve=false
        val operations=mutableListOf<String>()
        override fun modes()=supported
        override fun read(field:String)=mode
        override fun validate(field:String,bundle:Boolean) {if(missingReserve && field=="SREV_SOC_SET")error("Missing enum")}
        override fun write(field:String,value:Int,bundle:Boolean) {operations+="write:$field:$value:$bundle"}
        override fun confirm(field:String,value:Int) {operations+="confirm:$field:$value";if(reject)error("No feedback");if(field=="IVI_SOC_MODESET")mode=value}
    }
    @Test fun memoryMatchesRahaForEverySupportedFactoryMode() {
        for (modes in listOf(EnergyStrategyPolicy.legacyModes(true),
            EnergyStrategyPolicy.modes(true,true,false), EnergyStrategyPolicy.modes(true,false,true))) {
            for ((name, value) in modes) {
                val voice = Port(modes).apply { mode = if (value == 2) 3 else 2 }
                val memory = Port(modes).apply { mode = voice.mode }
                EnergyCommandExecutor(voice).execute("SET_ENERGY_STRATEGY", mapOf("strategy" to name))
                EnergyCommandExecutor(memory).restoreMode(value)
                assertEquals(voice.operations, memory.operations)
                assertFalse(memory.operations.any { it.contains("SREV_SOC_SET") })
            }
        }
    }
    @Test fun memoryRejectsUnsupportedAndUnknownAndSkipsAlreadySelected() {
        val same = Port()
        EnergyCommandExecutor(same).restoreMode(2)
        assertTrue(same.operations.isEmpty())
        for ((p, value) in listOf(Port() to 1, Port(emptyMap()) to 2,
            Port().apply { mode = 255 } to 4, Port() to 5)) {
            rejected { EnergyCommandExecutor(p).restoreMode(value) }
            assertTrue(p.operations.isEmpty())
        }
        val failed = Port().apply { reject = true }
        rejected { EnergyCommandExecutor(failed).restoreMode(4) }
        assertEquals(2, failed.operations.size)
    }
    private fun rejected(block:()->Unit) {try {block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun installedMenuMappingSelectsHybridFuelAndElectric() {
        for((name,value) in mapOf("hybrid" to 3,"fuel" to 4,"electric" to 2)) {
            val p=Port().apply {mode=1}
            EnergyCommandExecutor(p).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to name))
            assertEquals(listOf("write:IVI_SOC_MODESET:$value:true","confirm:IVI_SOC_MODESET:$value"),p.operations)
        }
    }
    @Test fun unsupportedModeAndUnknownCapabilitiesDoNotWrite() {
        for(p in listOf(Port(),Port(emptyMap()))) {
            rejected {EnergyCommandExecutor(p).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to "save"))}
            assertTrue(p.operations.isEmpty())
        }
    }
    @Test fun currentModeDoesNotToggleOrRewrite() {
        val p=Port();EnergyCommandExecutor(p).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to "electric"))
        assertTrue(p.operations.isEmpty())
    }
    @Test fun modeMustBeConfirmedBeforeReserveWrite() {
        val p=Port(EnergyStrategyPolicy.modes(true,true,false))
        EnergyCommandExecutor(p).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to 50))
        assertEquals(listOf("write:IVI_SOC_MODESET:4:true","confirm:IVI_SOC_MODESET:4","write:SREV_SOC_SET:5:false","confirm:SREV_SOC_SET:5"),p.operations)
        val failing=Port(p.supported).apply {reject=true}
        rejected {EnergyCommandExecutor(failing).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to 50))}
        assertFalse(failing.operations.any {it.contains("SREV_SOC_SET")})
    }
    @Test fun validationIsCompleteBeforeFirstWrite() {
        val p=Port(EnergyStrategyPolicy.modes(true,true,false)).apply {missingReserve=true}
        rejected {EnergyCommandExecutor(p).execute("SET_ENERGY_STRATEGY",mapOf("strategy" to "save","reservePercent" to 50))}
        assertTrue(p.operations.isEmpty())
        for(value in listOf(24,81,53,50.5,Double.NaN)) {
            val invalid=Port(p.supported)
            rejected {EnergyCommandExecutor(invalid).execute("SET_BATTERY_RESERVE_TARGET",mapOf("percent" to value))}
            assertTrue(invalid.operations.isEmpty())
        }
    }
    @Test fun standaloneReserveRequiresCurrentReserveMode() {
        val p=Port()
        rejected {EnergyCommandExecutor(p).execute("SET_BATTERY_RESERVE_TARGET",mapOf("percent" to 50))}
        assertTrue(p.operations.isEmpty())
        p.mode=4;EnergyCommandExecutor(p).execute("SET_BATTERY_RESERVE_TARGET",mapOf("percent" to 50))
        assertEquals(listOf("write:SREV_SOC_SET:5:false","confirm:SREV_SOC_SET:5"),p.operations)
    }
}
