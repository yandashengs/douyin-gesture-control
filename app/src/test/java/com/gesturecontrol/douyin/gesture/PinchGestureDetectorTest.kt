package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PinchGestureDetectorTest {
    private val classifier = HandPoseClassifier()

    @Test
    fun pointClassifiedOpenToClosedTriggersPinchClose() {
        val detector = PinchGestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithRatio(0.80f), 1_000L, 200L)
        events += feed(detector, pointWithRatio(0.15f), 1_240L, 200L)

        assertEquals(1, events.count { it is GestureEvent.PinchClose })
        assertFalse(events.any { it is GestureEvent.PinchExpand })
    }

    @Test
    fun pointClassifiedClosedToOpenTriggersPinchExpand() {
        val detector = PinchGestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithRatio(0.15f), 1_000L, 200L)
        events += feed(detector, pointWithRatio(0.80f), 1_240L, 200L)

        assertEquals(1, events.count { it is GestureEvent.PinchExpand })
        assertFalse(events.any { it is GestureEvent.PinchClose })
    }

    @Test
    fun initialConfirmedStateDoesNotEmitVolumeEvent() {
        val events = feed(PinchGestureDetector(), pointWithRatio(0.15f), 1_000L, 300L)

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun valuesInsideHysteresisDoNotEmitVolumeEvent() {
        val detector = PinchGestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithRatio(0.80f), 1_000L, 200L)
        events += feed(detector, pointWithRatio(0.40f), 1_240L, 500L)

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun missingHandResetsConfirmedState() {
        val detector = PinchGestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithRatio(0.80f), 1_000L, 200L)
        events += detector.process(null, 1_600L)
        events += feed(detector, pointWithRatio(0.15f), 1_640L, 200L)

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    private fun pointWithRatio(ratio: Float): HandFrame {
        val frame = classifier.classify(
            HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.point(), ratio)
        )
        assertEquals(HandPose.POINT, frame.pose)
        return frame
    }

    private fun feed(
        detector: PinchGestureDetector,
        frame: HandFrame,
        from: Long,
        duration: Long,
    ): List<GestureEvent> = buildList {
        var time = from
        while (time <= from + duration) {
            add(detector.process(frame, time))
            time += 40L
        }
    }
}
