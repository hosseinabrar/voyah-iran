package com.abrar.voyahiran.feature.voice

import com.abrar.raha.feedback.*
import org.junit.Assert.*
import org.junit.Test

class FeedbackStateMachineTest {
    @Test fun misunderstoodAndFailedExecutionAreDifferentResults() {
        val state=FeedbackStateMachine()
        state.begin(0);state.text(100,"فرمان نامفهوم")
        state.unrecognized(200)
        assertEquals(FeedbackState.UNRECOGNIZED,state.state)
        assertFalse(state.tick(1499))
        assertEquals(FeedbackState.UNRECOGNIZED,state.state)
        state.tick(1500)
        assertEquals(FeedbackState.HIDDEN,state.state)
        state.begin(2000);state.processing();state.result(2100,false)
        assertEquals(FeedbackState.FAILURE,state.state)
        state.result(2200,true)
        assertEquals(FeedbackState.SUCCESS,state.state)
    }
    @Test fun wakeAlonePlaysOnlyOnceAfterTwoSeconds() {
        val state=FeedbackStateMachine(); state.begin(0)
        assertFalse(state.tick(1999)); assertTrue(state.tick(2000)); assertFalse(state.tick(4000))
    }
    @Test fun joinedCommandNeverPlaysReadySound() {
        val state=FeedbackStateMachine(); state.text(0,"کولر رو روشن کن"); state.begin(10)
        assertFalse(state.tick(2000)); assertEquals(FeedbackState.LISTENING,state.state)
    }
    @Test fun partialCommandBeforeDeadlineSuppressesReady() {
        val state=FeedbackStateMachine(); state.begin(0); state.text(1900,"کولر")
        assertFalse(state.tick(2100))
    }
    @Test fun ongoingAudioDelaysToneUntilTextCanArrive() {
        val state=FeedbackStateMachine(); state.begin(0); state.speech(1950,true)
        assertFalse(state.tick(2000)); state.text(2100,"شیشه")
        assertFalse(state.tick(2600))
    }
    @Test fun cancellationClearsPendingToneAndOverlay() {
        val state=FeedbackStateMachine(); state.begin(0); state.cancel()
        assertFalse(state.tick(3000)); assertEquals(FeedbackState.HIDDEN,state.state)
    }
    @Test fun newInteractionAfterCancelHasItsOwnDeadline() {
        val state=FeedbackStateMachine(); state.begin(0); state.cancel(); state.begin(1500)
        assertFalse(state.tick(2000)); assertTrue(state.tick(3500))
    }
    @Test fun outcomeExpiresWithoutReadySound() {
        for(success in listOf(true,false)) {
            val state=FeedbackStateMachine(); state.begin(0); state.processing(); state.result(1000,success)
            assertEquals(if(success) FeedbackState.SUCCESS else FeedbackState.FAILURE,state.state)
            assertFalse(state.tick(2000)); state.tick(2300)
            assertEquals(FeedbackState.HIDDEN,state.state)
        }
    }
    @Test fun manualStartDoesNotScheduleASecondReadySound() {
        val state=FeedbackStateMachine(); state.begin(0,delayedReady=false)
        assertFalse(state.tick(3000)); assertEquals(FeedbackState.WAITING,state.state)
    }
    @Test fun newJoinedCommandDoesNotInheritPreviousResultExpiry() {
        val state=FeedbackStateMachine(); state.result(0,true); state.processing(); state.tick(2000)
        assertEquals(FeedbackState.PROCESSING,state.state)
    }
}
