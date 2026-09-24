package com.abrar.voyahiran.feature.display
import org.junit.Test
import org.junit.Assert.*
class ScreenSwapPressTest {
    @Test fun consumesDownUpShortAndOnlyExecutesShort() {
        val p=ScreenSwapPress()
        assertEquals(true to false,p.route(4096,true))
        assertEquals(true to false,p.route(8192,false))
        assertEquals(true to true,p.route(65536,false))
    }
    @Test fun ineligiblePressNeverChangesOwnerMidPress() {
        val p=ScreenSwapPress()
        assertEquals(false to false,p.route(4096,false))
        assertEquals(false to false,p.route(8192,true))
        assertEquals(false to false,p.route(65536,true))
    }
    @Test fun nextPressUsesCurrentEligibility() {
        val p=ScreenSwapPress();p.route(4096,true);p.route(65536,true)
        assertEquals(false to false,p.route(4096,false))
        assertEquals(false to false,p.route(65536,false))
    }
}
