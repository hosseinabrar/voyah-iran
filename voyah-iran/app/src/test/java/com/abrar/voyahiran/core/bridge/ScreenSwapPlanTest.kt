package com.abrar.voyahiran.core.bridge
import org.junit.Test
import org.junit.Assert.*
class ScreenSwapPlanTest {
    private fun app(id:Int, d:Int, pkg:String="music") = ScreenSwapPlan.Stack(id,d,pkg,"Main",1,1,true,true)
    private fun home(d:Int) = ScreenSwapPlan.Stack(100+d,d,"launcher","Home",2,1,true,false)
    @Test fun centerToPassengerHome() { val a=app(1,0); assertEquals(listOf(a),ScreenSwapPlan.moves(listOf(a,home(1)))) }
    @Test fun passengerToCenterHome() { val a=app(1,1); assertEquals(listOf(a),ScreenSwapPlan.moves(listOf(home(0),a))) }
    @Test fun secondaryLauncherIsNotStandardHomeType() {
        val h=home(1).copy(pkg="com.qinggan.app.launcher",activity="com.qinggan.secondlauncher.activity.SecondMainActivity",type=1)
        assertEquals(listOf(app(1,0)),ScreenSwapPlan.moves(listOf(app(1,0),h)))
    }
    @Test fun swapsBothPinnedIdentities() { val a=app(1,0);val b=app(2,1,"neshan"); assertEquals(listOf(b,a),ScreenSwapPlan.moves(listOf(a,b))) }
    @Test fun nextAppDoesNotReuseFirstIdentity() { val a=app(1,0);val n=app(2,0,"neshan"); assertNotEquals(ScreenSwapPlan.signature(listOf(a)),ScreenSwapPlan.signature(listOf(n))); assertEquals(listOf(n),ScreenSwapPlan.moves(listOf(n,a.copy(visible=false),home(1)))) }
    @Test fun doesNotMoveCameraOrMultiWindow() {
        assertEquals(listOf(app(1,0)),ScreenSwapPlan.moves(listOf(app(1,0),app(2,1,"camera").copy(userApp=false))))
        assertTrue(ScreenSwapPlan.moves(listOf(app(1,0).copy(mode=5),home(1))).isEmpty())
    }
    @Test fun missingDisplayOrHomeOnlyFallsBack() {
        assertTrue(ScreenSwapPlan.moves(listOf(app(1,0))).isEmpty())
        assertTrue(ScreenSwapPlan.moves(listOf(home(0),home(1))).isEmpty())
    }
    @Test fun bothHomesAreEmptyButMissingDisplayIsNot() {
        assertTrue(ScreenSwapPlan.empty(listOf(home(0),home(1))))
        assertFalse(ScreenSwapPlan.empty(listOf(home(0))))
        assertFalse(ScreenSwapPlan.empty(listOf(app(1,0),home(1))))
    }
    @Test fun hiddenAppUnderHomeDoesNotTransfer() {
        assertTrue(ScreenSwapPlan.empty(listOf(home(0), app(1,0).copy(visible=false), home(1))))
        assertTrue(ScreenSwapPlan.moves(listOf(home(0), app(1,0).copy(visible=false), home(1))).isEmpty())
    }
    @Test fun secondHomeStandardTypeIsEmpty() {
        val h=home(1).copy(pkg="com.qinggan.app.launcher",activity="com.qinggan.secondlauncher.activity.SecondMainActivity",type=1)
        assertTrue(ScreenSwapPlan.empty(listOf(home(0),h)))
    }
    @Test fun passengerMusicMovesOverCenterVehicle() {
        val music=app(1,1); val vehicle=app(2,0,"com.qinggan.app.vehicle").copy(userApp=false)
        assertEquals(listOf(music),ScreenSwapPlan.moves(listOf(vehicle,music)))
        assertFalse(ScreenSwapPlan.empty(listOf(vehicle,music)))
    }
    @Test fun centerMusicMovesOverPassengerExcludedApp() {
        val music=app(1,0); val vehicle=app(2,1,"com.qinggan.app.vehicle").copy(userApp=false)
        assertEquals(listOf(music),ScreenSwapPlan.moves(listOf(music,vehicle)))
    }
    @Test fun excludedAppsOnBothDisplaysAreEmpty() {
        val a=app(1,0).copy(userApp=false); val b=app(2,1).copy(userApp=false)
        assertTrue(ScreenSwapPlan.moves(listOf(a,b)).isEmpty())
        assertTrue(ScreenSwapPlan.empty(listOf(a,b)))
    }
    @Test fun appBehindExcludedForegroundIsNotMoved() {
        val vehicle=app(2,0,"vehicle").copy(userApp=false)
        val music=app(1,1)
        assertEquals(listOf(music),ScreenSwapPlan.moves(listOf(vehicle,app(3,0).copy(visible=false),music)))
    }
}
