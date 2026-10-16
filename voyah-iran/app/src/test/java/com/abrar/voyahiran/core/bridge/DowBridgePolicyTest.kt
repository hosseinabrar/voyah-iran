package com.abrar.voyahiran.core.bridge
import org.junit.Assert.*
import org.junit.Test
class DowBridgePolicyTest {
    @Test fun onlyBoundedRuntimeCommandsAllowed() {
        for (op in listOf("enable", "disable", "status"))
            assertEquals(listOf("dow-light",op), BridgePolicy.resolve(listOf("dow-light",op)))
        for (args in listOf(listOf("dow-light"),listOf("dow-light","3"),listOf("dow-light","enable","extra"),listOf("dow-light","red")))
            assertNull(BridgePolicy.resolve(args))
    }
}
