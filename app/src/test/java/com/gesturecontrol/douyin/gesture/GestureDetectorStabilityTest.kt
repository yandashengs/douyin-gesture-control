package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GestureDetectorStabilityTest {

    @Test
    fun oneWrongFrameDoesNotCreateFistThenOpen() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_000L, 600L)
        events += detector.process(HandLandmarkFixtures.fist(), 1_640L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_680L, 600L)

        assertFalse(events.any { it is GestureEvent.FistThenOpen })
    }

    @Test
    fun holdPosesFireOncePerEpisode() {
        val cases = listOf(
            HandLandmarkFixtures.vSign() to GestureEvent.VSignHold,
            HandLandmarkFixtures.point() to GestureEvent.PointHold,
            HandLandmarkFixtures.okSign() to GestureEvent.OkSign,
            HandLandmarkFixtures.rockSign() to GestureEvent.RockSign,
        )

        cases.forEach { (points, expected) ->
            val events = feed(GestureDetector(), points, 1_000L, 2_200L)
            assertEquals("${expected.label} should fire once", 1, events.count { it == expected })
        }
    }

    @Test
    fun leavingAndReturningAllowsSecondVSignHold() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.vSign(), 1_000L, 1_600L)
        events += feed(detector, HandLandmarkFixtures.other(), 2_640L, 600L)
        events += feed(detector, HandLandmarkFixtures.vSign(), 3_280L, 1_600L)

        assertEquals(2, events.count { it is GestureEvent.VSignHold })
    }

    @Test
    fun palmHoldRequiresStableLowMotionPalm() {
        val stationaryEvents = feed(
            GestureDetector(),
            HandLandmarkFixtures.openPalm(),
            1_000L,
            2_400L,
        )
        assertEquals(1, stationaryEvents.count { it is GestureEvent.PalmHold })

        val movingDetector = GestureDetector()
        val movingEvents = mutableListOf<GestureEvent>()
        var time = 1_000L
        repeat(60) { frame ->
            val y = if (frame % 2 == 0) 0.82f else 0.74f
            movingEvents += movingDetector.process(
                HandLandmarkFixtures.withWrist(HandLandmarkFixtures.openPalm(), 0.5f, y),
                time,
            )
            time += 40L
        }
        assertFalse(movingEvents.any { it is GestureEvent.PalmHold })
    }

    @Test
    fun noHandFor250MsResetsPendingAndStableState() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.fist(), 1_000L, 600L)
        events += feed(detector, null, 1_640L, 400L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 2_080L, 800L)

        assertFalse(events.any { it is GestureEvent.FistThenOpen })
    }

    @Test
    fun repeatedTimestampDoesNotCorruptState() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += detector.process(HandLandmarkFixtures.openPalm(), 1_000L)
        events += detector.process(HandLandmarkFixtures.fist(), 1_000L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_040L, 800L)

        assertFalse(events.any { it is GestureEvent.FistThenOpen })
    }

    private fun feed(
        detector: GestureDetector,
        points: List<HandPoint>?,
        from: Long,
        duration: Long,
    ): List<GestureEvent> = buildList {
        var time = from
        while (time <= from + duration) {
            add(detector.process(points, time))
            time += 40L
        }
    }
}
