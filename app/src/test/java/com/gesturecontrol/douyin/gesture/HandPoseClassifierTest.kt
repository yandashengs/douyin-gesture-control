package com.gesturecontrol.douyin.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class HandPoseClassifierTest {
    private val classifier = HandPoseClassifier()

    @Test
    fun classifiesOpenPalm() = assertPose(HandPose.OPEN_PALM, HandLandmarkFixtures.openPalm())

    @Test
    fun classifiesFist() = assertPose(HandPose.FIST, HandLandmarkFixtures.fist())

    @Test
    fun classifiesVSign() = assertPose(HandPose.V_SIGN, HandLandmarkFixtures.vSign())

    @Test
    fun classifiesPoint() = assertPose(HandPose.POINT, HandLandmarkFixtures.point())

    @Test
    fun classifiesOkBeforeGenericMasks() =
        assertPose(HandPose.OK_SIGN, HandLandmarkFixtures.okSign())

    @Test
    fun classifiesRockSign() = assertPose(HandPose.ROCK_SIGN, HandLandmarkFixtures.rockSign())

    @Test
    fun classifiesAmbiguousShapeAsOther() =
        assertPose(HandPose.OTHER, HandLandmarkFixtures.other())

    @Test
    fun frameExposesPalmCenterWidthAndExtendedFingerCount() {
        val frame = classifier.classify(HandLandmarkFixtures.threeFingerPalm())

        assertEquals(HandPose.OTHER, frame.pose)
        assertEquals(0.532f, frame.palmX, 0.001f)
        assertEquals(0.680f, frame.palmY, 0.001f)
        assertEquals(0.24331f, frame.palmWidth, 0.001f)
        assertEquals(3, frame.extendedFingerCount)
    }

    @Test
    fun fistHasNoExtendedFingers() {
        assertEquals(0, classifier.classify(HandLandmarkFixtures.fist()).extendedFingerCount)
    }

    @Test
    fun motionBentJointsStillExposeFourOpenFingers() {
        val frame = classifier.classify(HandLandmarkFixtures.motionBlurredOpenPalm())

        assertEquals(HandPose.OTHER, frame.pose)
        assertEquals(4, frame.extendedFingerCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteLandmarkList() {
        classifier.classify(emptyList())
    }

    private fun assertPose(expected: HandPose, points: List<HandPoint>) {
        assertEquals(expected, classifier.classify(points).pose)
    }
}
