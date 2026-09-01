package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PalmSwipeDetectorTest {
    private val classifier = HandPoseClassifier()

    @Test
    fun threeFingerPalmLabeledOtherCanSwipeUp() {
        val points = HandLandmarkFixtures.threeFingerPalm()
        assertEquals(HandPose.OTHER, classifier.classify(points).pose)

        val events = move(points, startY = 0.76f, endY = 0.58f)

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun threeFingerPalmLabeledOtherCanSwipeDown() {
        val events = move(
            HandLandmarkFixtures.threeFingerPalm(),
            startY = 0.58f,
            endY = 0.76f,
        )

        assertEquals(1, events.count { it is GestureEvent.SwipeDown })
    }

    @Test
    fun equivalentPalmWidthNormalizedMovesBothTrigger() {
        val normal = move(
            HandLandmarkFixtures.threeFingerPalm(),
            startY = 0.76f,
            endY = 0.58f,
        )
        val small = move(
            HandLandmarkFixtures.scaled(HandLandmarkFixtures.threeFingerPalm(), 0.60f),
            startY = 0.70f,
            endY = 0.592f,
        )

        assertEquals(1, normal.count { it is GestureEvent.SwipeUp })
        assertEquals(1, small.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun twoMissingFramesDoNotBreakSwipe() {
        val detector = PalmSwipeDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        val events = buildList {
            repeat(11) { index ->
                val time = 1_000L + index * 40L
                if (index == 4 || index == 5) {
                    add(detector.process(null, time))
                } else {
                    add(detector.process(frameAt(points, 0.50f, 0.76f - 0.018f * index), time))
                }
            }
        }

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun temporaryTwoFingerClassificationDoesNotBreakArmedSwipe() {
        val detector = PalmSwipeDetector()
        val open = HandLandmarkFixtures.threeFingerPalm()
        val noisy = HandLandmarkFixtures.other()
        val events = buildList {
            repeat(11) { index ->
                val points = if (index in 4..5) noisy else open
                add(
                    detector.process(
                        frameAt(points, 0.50f, 0.76f - 0.018f * index),
                        1_000L + index * 40L,
                    )
                )
            }
        }

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun fistMotionDoesNotSwipe() {
        val events = move(HandLandmarkFixtures.fist(), 0.76f, 0.50f)

        assertNoSwipe(events)
    }

    @Test
    fun horizontalPalmMotionDoesNotSwipe() {
        val events = move(
            HandLandmarkFixtures.threeFingerPalm(),
            startY = 0.70f,
            endY = 0.68f,
            startX = 0.40f,
            endX = 0.62f,
        )

        assertNoSwipe(events)
    }

    @Test
    fun stationaryPalmJitterDoesNotSwipe() {
        val detector = PalmSwipeDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        val events = buildList {
            repeat(20) { index ->
                val jitter = if (index % 2 == 0) -0.01f else 0.01f
                add(detector.process(frameAt(points, 0.50f + jitter, 0.70f - jitter), 1_000L + index * 40L))
            }
        }

        assertNoSwipe(events)
    }

    @Test
    fun unsupportedSingleFrameJumpDoesNotSwipe() {
        val detector = PalmSwipeDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        val events = buildList {
            repeat(5) { index ->
                add(detector.process(frameAt(points, 0.50f, 0.76f), 1_000L + index * 40L))
            }
            add(detector.process(frameAt(points, 0.50f, 0.54f), 1_200L))
            repeat(6) { index ->
                add(detector.process(frameAt(points, 0.50f, 0.76f), 1_240L + index * 40L))
            }
        }

        assertNoSwipe(events)
    }

    @Test
    fun oneMotionFiresOnlyOnceUntilReset() {
        val detector = PalmSwipeDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        val events = mutableListOf<GestureEvent>()
        events += move(detector, points, 1_000L, 0.76f, 0.58f)
        repeat(10) { index ->
            events += detector.process(frameAt(points, 0.50f, 0.56f - index * 0.01f), 1_440L + index * 40L)
        }

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun oppositeMotionAfterStillResetCanFireAgain() {
        val detector = PalmSwipeDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        val events = mutableListOf<GestureEvent>()
        events += move(detector, points, 1_000L, 0.76f, 0.58f)
        repeat(8) { index ->
            events += detector.process(frameAt(points, 0.50f, 0.58f), 1_440L + index * 40L)
        }
        events += move(detector, points, 1_800L, 0.58f, 0.76f)

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
        assertEquals(1, events.count { it is GestureEvent.SwipeDown })
    }

    private fun move(
        points: List<HandPoint>,
        startY: Float,
        endY: Float,
        startX: Float = 0.50f,
        endX: Float = startX,
    ): List<GestureEvent> = move(PalmSwipeDetector(), points, 1_000L, startY, endY, startX, endX)

    private fun move(
        detector: PalmSwipeDetector,
        points: List<HandPoint>,
        from: Long,
        startY: Float,
        endY: Float,
        startX: Float = 0.50f,
        endX: Float = startX,
    ): List<GestureEvent> = buildList {
        repeat(11) { index ->
            val progress = index / 10f
            add(
                detector.process(
                    frameAt(
                        points,
                        startX + (endX - startX) * progress,
                        startY + (endY - startY) * progress,
                    ),
                    from + index * 40L,
                )
            )
        }
    }

    private fun frameAt(points: List<HandPoint>, wristX: Float, wristY: Float): HandFrame =
        classifier.classify(HandLandmarkFixtures.withWrist(points, wristX, wristY))

    private fun assertNoSwipe(events: List<GestureEvent>) {
        assertFalse(events.any { it is GestureEvent.SwipeUp || it is GestureEvent.SwipeDown })
    }
}
