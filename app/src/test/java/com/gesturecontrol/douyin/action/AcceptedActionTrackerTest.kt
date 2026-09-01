package com.gesturecontrol.douyin.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptedActionTrackerTest {

    @Test
    fun acceptedActionUpdatesState() {
        val tracker = AcceptedActionTracker { 123L }

        assertTrue(tracker.record(DouyinAction.SwipeUp, accepted = true))
        assertSame(DouyinAction.SwipeUp, tracker.lastAction)
        assertEquals(123L, tracker.lastActionTime)
    }

    @Test
    fun rejectedActionDoesNotReplaceState() {
        val tracker = AcceptedActionTracker { 1L }
        tracker.record(DouyinAction.SwipeUp, accepted = true)

        assertFalse(tracker.record(DouyinAction.SwipeDown, accepted = false))
        assertSame(DouyinAction.SwipeUp, tracker.lastAction)
        assertEquals(1L, tracker.lastActionTime)
    }
}
