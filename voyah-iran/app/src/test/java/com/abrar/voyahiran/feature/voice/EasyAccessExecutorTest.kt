package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class EasyAccessExecutorTest {
    private class Port : DriverAssistancePort {
        // No speed, manual height or seat-movement fields are available.
        val states = mutableMapOf("AUTO_EASY_ENTER_SET" to 1, "DSCU_EASY_ENTER_SET" to 1)
        val writes = mutableListOf<Pair<String, Int>>()
        var acknowledge = true
        override fun validate(field: String) { check(states.containsKey(field)) }
        override fun read(field: String) = states.getValue(field)
        override fun write(field: String, value: Int) {
            writes += field to value
            if (acknowledge) states[field] = value
        }
        override fun confirm(field: String, value: Int) { check(read(field) == value) }
        override fun pause(milliseconds: Long) {}
    }
    @Test fun exactOemPreferenceFieldsAndPolarityWithoutImmediateMovement() {
        for ((action, field) in listOf("SET_EASY_ENTRY" to "AUTO_EASY_ENTER_SET", "SET_EASY_EXIT" to "DSCU_EASY_ENTER_SET")) {
            val p=Port(); val executor=DriverAssistanceExecutor(p)
            executor.execute(action,mapOf("enabled" to true))
            executor.execute(action,mapOf("enabled" to true))
            executor.execute(action,mapOf("enabled" to false))
            assertEquals(listOf(field to 2, field to 1),p.writes)
        }
    }
    @Test fun seatExitDoesNotDependOnSuspensionFields() {
        for (enabled in listOf(true,false)) {
            val p=Port()
            p.states.remove("AUTO_EASY_ENTER_SET")
            p.states["DSCU_EASY_ENTER_SET"] = if (enabled) 1 else 2
            DriverAssistanceExecutor(p).execute("SET_EASY_EXIT",mapOf("enabled" to enabled))
            assertEquals(listOf("DSCU_EASY_ENTER_SET" to if (enabled) 2 else 1),p.writes)
        }
    }
    @Test fun unknownFeedbackAndMalformedParametersFailClosed() {
        for (action in listOf("SET_EASY_ENTRY", "SET_EASY_EXIT")) {
            val p=Port(); p.states[DriverAssistanceExecutor.plan(action,mapOf("enabled" to true)).single().feedback]=255
            assertThrows(IllegalStateException::class.java) { DriverAssistanceExecutor(p).execute(action,mapOf("enabled" to true)) }
            assertTrue(p.writes.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { DriverAssistanceExecutor.plan(action,mapOf("enabled" to "true")) }
        }
    }
    @Test fun unconfirmedWriteIsNotSuccess() {
        for (action in listOf("SET_EASY_ENTRY", "SET_EASY_EXIT")) {
            val p=Port();p.acknowledge=false
            assertThrows(IllegalStateException::class.java) { DriverAssistanceExecutor(p).execute(action,mapOf("enabled" to true)) }
        }
    }
}
