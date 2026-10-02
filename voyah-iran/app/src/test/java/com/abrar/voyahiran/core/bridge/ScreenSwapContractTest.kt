package com.abrar.voyahiran.core.bridge
import org.junit.Test
import org.junit.Assert.*
class ScreenSwapContractTest {
    @Test fun legacyTransferIsBlockedUntilPassengerImeAccessIsFixed() {
        assertNull(ScreenSwapContract.forSdk(28))
    }
    @Test fun android11KeepsTaskManager() {
        assertEquals(ScreenSwapContract("android.app.ActivityTaskManager", "android.app.IActivityTaskManager"), ScreenSwapContract.forSdk(30))
    }
    @Test fun unknownPlatformsStayDisabled() {
        for (sdk in listOf(27,29,31)) assertNull(ScreenSwapContract.forSdk(sdk))
    }
}
