package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDetectorDynamicTest {

    @Test
    fun quickPointClassifiedPinchCloseTriggersBeforePointHold() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithPinch(0.80f), 1_000L, 200L)
        events += feed(detector, pointWithPinch(0.15f), 1_240L, 200L)

        assertEquals(1, events.count { it is GestureEvent.PinchClose })
        assertFalse(events.any { it is GestureEvent.PointHold })
    }

    @Test
    fun quickPointClassifiedPinchExpandTriggersBeforePointHold() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, pointWithPinch(0.15f), 1_000L, 200L)
        events += feed(detector, pointWithPinch(0.80f), 1_240L, 200L)

        assertEquals(1, events.count { it is GestureEvent.PinchExpand })
        assertFalse(events.any { it is GestureEvent.PointHold })
    }

    @Test
    fun stationaryPointStillTriggersPointHold() {
        val events = feed(GestureDetector(), pointWithPinch(0.80f), 1_000L, 1_200L)

        assertEquals(1, events.count { it is GestureEvent.PointHold })
        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun otherLabeledOpenHandSwipeWinsOverStaticEvents() {
        val detector = GestureDetector()
        val points = HandLandmarkFixtures.threeFingerPalm()
        assertEquals(HandPose.OTHER, HandPoseClassifier().classify(points).pose)

        val events = poseTrajectory(detector, points, 1_000L, 0.50f, 0.76f, 0.50f, 0.58f)

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
        assertFalse(events.any { it is GestureEvent.PalmHold || it is GestureEvent.PinchClose })
    }

    @Test
    fun movingFistAndVSignDoNotSwipe() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += poseTrajectory(
            detector,
            HandLandmarkFixtures.fist(),
            1_000L,
            0.50f,
            0.76f,
            0.50f,
            0.50f,
        )
        events += poseTrajectory(
            detector,
            HandLandmarkFixtures.vSign(),
            2_000L,
            0.50f,
            0.76f,
            0.50f,
            0.50f,
        )

        assertFalse(events.any { it is GestureEvent.SwipeUp || it is GestureEvent.SwipeDown })
    }

    @Test
    fun slowConsistentUpwardTrajectoryTriggersSwipeUp() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, palmAt(0.50f, 0.72f), 1_000L, 700L)
        events += trajectory(detector, 1_740L, 0.50f, 0.72f, 0.50f, 0.54f)

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
        assertFalse(events.any { it is GestureEvent.SwipeDown })
    }

    @Test
    fun slowConsistentDownwardTrajectoryTriggersSwipeDown() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, palmAt(0.50f, 0.54f), 1_000L, 700L)
        events += trajectory(detector, 1_740L, 0.50f, 0.54f, 0.50f, 0.72f)

        assertEquals(1, events.count { it is GestureEvent.SwipeDown })
        assertFalse(events.any { it is GestureEvent.SwipeUp })
    }

    @Test
    fun horizontalMotionDoesNotTriggerSwipe() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, palmAt(0.40f, 0.72f), 1_000L, 700L)
        events += trajectory(detector, 1_740L, 0.40f, 0.72f, 0.62f, 0.69f)

        assertFalse(events.any { it is GestureEvent.SwipeUp || it is GestureEvent.SwipeDown })
    }

    @Test
    fun oneFrameVerticalJumpDoesNotTriggerSwipe() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, palmAt(0.50f, 0.72f), 1_000L, 700L)
        events += detector.process(palmAt(0.50f, 0.50f), 1_740L)
        events += feed(detector, palmAt(0.50f, 0.72f), 1_780L, 500L)

        assertFalse(events.any { it is GestureEvent.SwipeUp || it is GestureEvent.SwipeDown })
    }

    @Test
    fun swipeSurvivesOtherPoseFramesAfterStablePalm() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, palmAt(0.50f, 0.72f), 1_000L, 700L)
        repeat(11) { frame ->
            val progress = frame / 10f
            events += detector.process(
                HandLandmarkFixtures.withWrist(
                    HandLandmarkFixtures.other(),
                    0.50f,
                    0.72f - 0.18f * progress,
                ),
                1_740L + frame * 40L,
            )
        }

        assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    }

    @Test
    fun stableFistThenStableOpenTriggersOnce() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.fist(), 1_000L, 700L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_740L, 800L)

        assertEquals(1, events.count { it is GestureEvent.FistThenOpen })
    }

    @Test
    fun oneFistFrameDoesNotTriggerFistThenOpen() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_000L, 600L)
        events += detector.process(HandLandmarkFixtures.fist(), 1_640L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 1_680L, 800L)

        assertFalse(events.any { it is GestureEvent.FistThenOpen })
    }

    @Test
    fun fistThenOpenAfter900MsDoesNotTrigger() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, HandLandmarkFixtures.fist(), 1_000L, 1_700L)
        events += feed(detector, HandLandmarkFixtures.openPalm(), 2_740L, 800L)

        assertFalse(events.any { it is GestureEvent.FistThenOpen })
    }

    @Test
    fun initialPinchInOkSignDoesNotEmitEvent() {
        // OK 手势本身 pinchRatio 很低（拇指食指成圈），不应被误判为 PinchClose。
        // OK_SIGN 状态下初始化不触发事件，但 OkSign hold 正常触发。
        val events = feed(GestureDetector(), HandLandmarkFixtures.okSign(), 1_000L, 700L)

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun initialPinchInOtherEstablishesBaselineWithoutVolumeEvent() {
        val detector = GestureDetector()
        val events = feed(
            detector,
            HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.other(), 0.70f),
            1_000L,
            700L,
        )

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun openToClosedFor200MsTriggersPinchClose() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        // phase 1 需 ≥800ms：5 帧投票(~200ms) + poseConfirmMs(180ms) + pinchConfirmMs(200ms) ≈ 580ms
        events += feed(detector, otherWithPinch(0.70f), 1_000L, 800L)
        events += feed(detector, otherWithPinch(0.20f), 1_840L, 400L)

        assertEquals(1, events.count { it is GestureEvent.PinchClose })
    }

    @Test
    fun closedToOpenFor200MsTriggersPinchExpand() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        events += feed(detector, otherWithPinch(0.20f), 1_000L, 800L)
        events += feed(detector, otherWithPinch(0.70f), 1_840L, 400L)

        assertEquals(1, events.count { it is GestureEvent.PinchExpand })
    }

    @Test
    fun thresholdJitterDoesNotTriggerPinch() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        var time = 1_000L
        // 抖动比在新阈值 (pinchClosedRatio=0.25, pinchOpenRatio=0.65) 内交替
        repeat(30) { frame ->
            val ratio = if (frame % 2 == 0) 0.20f else 0.70f
            events += detector.process(otherWithPinch(ratio), time)
            time += 40L
        }

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun nonPinchPoseChangesDoNotTriggerVolume() {
        val detector = GestureDetector()
        val events = mutableListOf<GestureEvent>()
        // 第一段用中间区间 pinchRatio（0.40），避免触发 OTHER 状态下的 Pinch 初始化事件。
        // 测试意图：pose 在 OTHER→FIST→POINT 之间变化时，不应触发音量事件。
        events += feed(detector, otherWithPinch(0.40f), 1_000L, 700L)
        events += feed(
            detector,
            HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.fist(), 0.15f),
            1_740L,
            400L,
        )
        events += feed(
            detector,
            HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.point(), 0.90f),
            2_180L,
            400L,
        )

        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    @Test
    fun okPoseDoesNotTriggerVolumeEvents() {
        val events = feed(GestureDetector(), HandLandmarkFixtures.okSign(), 1_000L, 2_000L)

        assertTrue(events.any { it is GestureEvent.OkSign })
        assertFalse(events.any { it is GestureEvent.PinchClose || it is GestureEvent.PinchExpand })
    }

    private fun trajectory(
        detector: GestureDetector,
        from: Long,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): List<GestureEvent> = buildList {
        repeat(11) { frame ->
            val progress = frame / 10f
            add(
                detector.process(
                    palmAt(
                        startX + (endX - startX) * progress,
                        startY + (endY - startY) * progress,
                    ),
                    from + frame * 40L,
                )
            )
        }
    }

    private fun poseTrajectory(
        detector: GestureDetector,
        points: List<HandPoint>,
        from: Long,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): List<GestureEvent> = buildList {
        repeat(11) { frame ->
            val progress = frame / 10f
            add(
                detector.process(
                    HandLandmarkFixtures.withWrist(
                        points,
                        startX + (endX - startX) * progress,
                        startY + (endY - startY) * progress,
                    ),
                    from + frame * 40L,
                )
            )
        }
    }

    private fun feed(
        detector: GestureDetector,
        points: List<HandPoint>,
        from: Long,
        duration: Long,
    ): List<GestureEvent> = buildList {
        var time = from
        while (time <= from + duration) {
            add(detector.process(points, time))
            time += 40L
        }
    }

    private fun palmAt(x: Float, y: Float): List<HandPoint> =
        HandLandmarkFixtures.withWrist(HandLandmarkFixtures.openPalm(), x, y)

    private fun otherWithPinch(ratio: Float): List<HandPoint> =
        HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.other(), ratio)

    private fun pointWithPinch(ratio: Float): List<HandPoint> =
        HandLandmarkFixtures.withPinchRatio(HandLandmarkFixtures.point(), ratio)
}
