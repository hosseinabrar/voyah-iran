package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test
import com.abrar.voyahiran.core.bridge.AvmTurnPolicy.*

class AvmTurnPolicyTest {
    private val on = Input(true, true, false, 10, true, false, Visibility.HIDDEN)
    private fun opened(p: AvmTurnPolicy): Input {
        assertEquals(Action.Open(Side.LEFT), p.step(on, 0))
        p.openResult(true, 0)
        val visible = on.copy(visibility = Visibility.USER)
        assertEquals(Action.Select(Side.LEFT), p.step(visible, 100))
        return visible
    }
    @Test fun requiresAllOpeningConditions() {
        listOf(on.copy(enabled=false), on.copy(subscriptionActive=false), on.copy(reverse=true),
            on.copy(reverse=null), on.copy(speedKmh=null), on.copy(speedKmh=25), on.copy(speedKmh=-1),
            on.copy(left=null), on.copy(right=true), on.copy(visibility=Visibility.USER),
            on.copy(visibility=Visibility.UNKNOWN), on.copy(visibility=Visibility.REVERSE)).forEach {
            assertNull(it.toString(), AvmTurnPolicy().step(it, 0))
        }
        assertEquals(Action.Open(Side.RIGHT), AvmTurnPolicy().step(on.copy(left=false,right=true,speedKmh=24),0))
    }
    @Test fun waitsThreeSecondsAfterSwitchOff() {
        val p=AvmTurnPolicy(); val off=opened(p).copy(left=false)
        assertEquals(Action.Select(Side.FRONT),p.step(off,200)); assertNull(p.step(off,3199))
        assertEquals(Action.Close,p.step(off,3200)); assertNull(p.step(off,4000))
    }
    @Test fun renewedSignalCancelsCloseAndChangesSide() {
        val p=AvmTurnPolicy(); val visible=opened(p)
        assertEquals(Action.Select(Side.FRONT),p.step(visible.copy(left=false),200))
        val right=visible.copy(left=false,right=true)
        assertEquals(Action.Select(Side.RIGHT),p.step(right,1000))
        assertNull(p.step(right,5000))
        assertEquals(Action.Select(Side.FRONT),p.step(right.copy(right=false),6000))
        assertEquals(Action.Close,p.step(right.copy(right=false),9000))
    }
    @Test fun reverseCancelsPendingCloseAndDoesNotReopen() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(left=false),200)
        assertNull(p.step(v.copy(reverse=true),4000))
        assertNull(p.step(on,5000))
    }
    @Test fun manualHideAndReopenIsNeverClosed() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(left=false),200)
        p.visibilityChanged(Visibility.HIDDEN)
        p.visibilityChanged(Visibility.USER)
        assertNull(p.step(v.copy(left=false),4000))
    }
    @Test fun manualDismissSuppressesReopeningUntilSignalOff() {
        val p=AvmTurnPolicy(); opened(p)
        p.visibilityChanged(Visibility.HIDDEN)
        assertNull(p.step(on,5000))
        assertNull(p.step(on.copy(left=false),6000))
        assertEquals(Action.Open(Side.LEFT),p.step(on,7000))
    }
    @Test fun openFailureDoesNotRetryContinuously() {
        val p=AvmTurnPolicy(); p.step(on,0); p.openResult(false,0)
        assertNull(p.step(on,5000))
    }
    @Test fun missingOpenAckDoesNotGrantOwnership() {
        val p=AvmTurnPolicy(); p.step(on,0); p.openResult(true,0)
        assertNull(p.step(on,1999)); assertNull(p.step(on,2000)); assertNull(p.step(on,5000))
    }
    @Test fun hideDuringOpeningDoesNotReopen() {
        val p=AvmTurnPolicy(); p.step(on,0); p.openResult(true,0)
        p.visibilityChanged(Visibility.HIDDEN)
        assertNull(p.step(on,100))
    }
    @Test fun unknownReverseCannotClose() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(left=false),200)
        assertNull(p.step(v.copy(left=false,reverse=null),4000))
    }
    @Test fun unknownSignalCancelsPendingClose() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(left=false),200)
        assertNull(p.step(v.copy(left=null),4000))
        assertEquals(Action.Select(Side.FRONT),p.step(v.copy(left=false),5000))
        assertEquals(Action.Close,p.step(v.copy(left=false),8000))
    }
    @Test fun disableClosesOnlyOwnedCamera() {
        val p=AvmTurnPolicy(); val v=opened(p)
        assertEquals(Action.Close,p.step(v.copy(enabled=false),200))
        assertNull(AvmTurnPolicy().step(v.copy(enabled=false),200))
    }
    @Test fun subscriptionExpiryClosesOwnedCameraButNeverReverse() {
        val p=AvmTurnPolicy(); val v=opened(p)
        assertEquals(Action.Close,p.step(v.copy(subscriptionActive=false),200))
        val reversePolicy=AvmTurnPolicy(); val r=opened(reversePolicy)
        assertNull(reversePolicy.step(r.copy(subscriptionActive=false,reverse=true),200))
    }
    @Test fun deferredSelectionCanRetryWithoutReopening() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.selectDeferred()
        assertNull(p.step(v.copy(speedKmh=null),200))
        assertEquals(Action.Select(Side.LEFT),p.step(v.copy(speedKmh=24),400))
        assertNull(p.step(v,600))
    }
    @Test fun pdcPreventsOpeningIncludingUnknownState() {
        assertNull(AvmTurnPolicy().step(on.copy(pdc=true),0))
        assertNull(AvmTurnPolicy().step(on.copy(pdc=null),0))
    }
    @Test fun pdcCancelsCloseDeadlineUntilReleased() {
        val p=AvmTurnPolicy(); val v=opened(p).copy(left=false)
        p.step(v,200)
        assertNull(p.step(v.copy(pdc=true),3200))
        assertNull(p.step(v.copy(pdc=true),9000))
        assertEquals(Action.Select(Side.FRONT),p.step(v,10000))
        assertNull(p.step(v,10999))
        assertEquals(Action.Close,p.step(v,11000))
    }
    @Test fun signalStopsDuringPdcAndClosesOneSecondAfterRelease() {
        val p=AvmTurnPolicy(); val v=opened(p)
        assertNull(p.step(v.copy(pdc=true),200))
        assertNull(p.step(v.copy(left=false,pdc=true),300))
        val off=v.copy(left=false,speedKmh=26)
        assertEquals(Action.Select(Side.FRONT),p.step(off,5000))
        assertNull(p.step(off,5999))
        assertEquals(Action.Close,p.step(off,6000))
    }
    @Test fun renewedPdcRestartsOneSecondDelay() {
        val p=AvmTurnPolicy(); val v=opened(p).copy(left=false)
        p.step(v.copy(pdc=true),200)
        p.step(v,1000)
        assertNull(p.step(v.copy(pdc=true),1500))
        p.step(v,3000)
        assertNull(p.step(v,3999))
        assertEquals(Action.Close,p.step(v,4000))
    }
    @Test fun signalResumptionCancelsPdcClose() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(left=false,pdc=true),200)
        p.step(v.copy(pdc=true),300)
        p.step(v,2000)
        assertEquals(Action.Select(Side.FRONT),p.step(v.copy(left=false),3000))
        assertNull(p.step(v.copy(left=false),5999))
        assertEquals(Action.Close,p.step(v.copy(left=false),6000))
    }
    @Test fun pdcPreventsSideChangesAndSubscriptionClose() {
        val p=AvmTurnPolicy(); val v=opened(p)
        assertNull(p.step(v.copy(left=false,right=true,pdc=true),200))
        assertNull(p.step(v.copy(subscriptionActive=false,pdc=true),400))
    }
    @Test fun pdcManualReopenIsNeverOwned() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.step(v.copy(pdc=true),200)
        p.visibilityChanged(Visibility.HIDDEN)
        p.visibilityChanged(Visibility.USER)
        assertNull(p.step(v.copy(left=false),5000))
        assertNull(p.step(v.copy(left=false),10000))
    }
    @Test fun sameSignalReturnsFromFrontAndRestartsCloseDelay() {
        val p=AvmTurnPolicy(); val v=opened(p)
        assertEquals(Action.Select(Side.FRONT),p.step(v.copy(left=false),200))
        assertEquals(Action.Select(Side.LEFT),p.step(v,1000))
        assertEquals(Action.Select(Side.FRONT),p.step(v.copy(left=false),2000))
        assertNull(p.step(v.copy(left=false),4999))
        assertEquals(Action.Close,p.step(v.copy(left=false),5000))
    }
    @Test fun manualCameraIsNeitherSwitchedToFrontNorClosed() {
        val p=AvmTurnPolicy(); val v=on.copy(visibility=Visibility.USER,left=false)
        assertNull(p.step(v,0)); assertNull(p.step(v,10000))
    }
    @Test fun forcedHideWhileSelectingCancelsOwnershipAndPreventsReopen() {
        val p=AvmTurnPolicy(); val v=opened(p)
        p.visibilityChanged(Visibility.HIDDEN)
        p.selectResult(false) // A pending selection finishes after the forced hide.
        assertNull(p.step(v.copy(speedKmh=30,visibility=Visibility.HIDDEN),200))
        assertNull(p.step(on,1000)) // Slowing down with the same signal must not reopen.
        assertNull(p.step(on.copy(left=false),1100))
        assertEquals(Action.Open(Side.LEFT),p.step(on,1200))
    }
    @Test fun speedIncreaseDoesNotCancelCloseWhileCameraRemainsVisible() {
        val p=AvmTurnPolicy(); val off=opened(p).copy(left=false)
        assertEquals(Action.Select(Side.FRONT),p.step(off,200))
        assertNull(p.step(off.copy(speedKmh=30),400))
        assertEquals(Action.Close,p.step(off.copy(speedKmh=26),3200))
        assertNull(p.step(off,4000))
    }
    @Test fun forceHiddenDuringDelayDoesNotCloseManuallyReopenedCamera() {
        val p=AvmTurnPolicy(); val off=opened(p).copy(left=false)
        p.step(off,200)
        p.visibilityChanged(Visibility.HIDDEN)
        p.visibilityChanged(Visibility.USER)
        assertNull(p.step(off,3200))
    }
    @Test fun speedIncreaseDuringOpeningRetainsOwnership() {
        val p=AvmTurnPolicy()
        assertEquals(Action.Open(Side.LEFT),p.step(on,0)); p.openResult(true,0)
        assertNull(p.step(on.copy(speedKmh=30),100))
        p.visibilityChanged(Visibility.USER)
        assertEquals(Action.Select(Side.LEFT),p.step(on.copy(visibility=Visibility.USER),200))
        assertEquals(Action.Select(Side.FRONT),p.step(on.copy(left=false,visibility=Visibility.USER,speedKmh=26),4000))
        assertEquals(Action.Close,p.step(on.copy(left=false,visibility=Visibility.USER,speedKmh=26),7000))
    }
}
