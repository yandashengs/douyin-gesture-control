package com.gesturecontrol.douyin.gesture

import kotlin.math.hypot

internal object HandLandmarkFixtures {

    fun openPalm(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = extendedMiddle(),
        ring = extendedRing(),
        pinky = extendedPinky(),
    )

    fun fist(): List<HandPoint> = hand(
        index = curledIndex(),
        middle = curledMiddle(),
        ring = curledRing(),
        pinky = curledPinky(),
    )

    fun vSign(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = extendedMiddle(),
        ring = curledRing(),
        pinky = curledPinky(),
    )

    fun point(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = curledMiddle(),
        ring = curledRing(),
        pinky = curledPinky(),
    )

    fun okSign(): List<HandPoint> = withPinchRatio(
        hand(
            index = curledIndex(),
            middle = extendedMiddle(),
            ring = extendedRing(),
            pinky = extendedPinky(),
        ),
        ratio = 0.16f,
    )

    fun rockSign(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = curledMiddle(),
        ring = curledRing(),
        pinky = extendedPinky(),
    )

    fun other(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = curledMiddle(),
        ring = extendedRing(),
        pinky = curledPinky(),
    )

    fun threeFingerPalm(): List<HandPoint> = hand(
        index = extendedIndex(),
        middle = extendedMiddle(),
        ring = extendedRing(),
        pinky = curledPinky(),
    )

    fun motionBlurredOpenPalm(): List<HandPoint> = openPalm().toMutableList().apply {
        this[7] = HandPoint(0.55f, 0.35f)
        this[11] = HandPoint(0.62f, 0.30f)
        this[15] = HandPoint(0.48f, 0.35f)
        this[19] = HandPoint(0.80f, 0.44f)
    }

    fun withWrist(points: List<HandPoint>, x: Float, y: Float): List<HandPoint> {
        val dx = x - points[0].x
        val dy = y - points[0].y
        return points.map { it.copy(x = it.x + dx, y = it.y + dy) }
    }

    fun withPinchRatio(points: List<HandPoint>, ratio: Float): List<HandPoint> {
        val palmSize = distance(points[0], points[9])
        val indexTip = points[8]
        return points.toMutableList().apply {
            this[4] = HandPoint(indexTip.x - ratio * palmSize, indexTip.y, indexTip.z)
        }
    }

    fun scaled(points: List<HandPoint>, factor: Float): List<HandPoint> {
        val anchor = points[0]
        return points.map {
            it.copy(
                x = anchor.x + (it.x - anchor.x) * factor,
                y = anchor.y + (it.y - anchor.y) * factor,
                z = anchor.z + (it.z - anchor.z) * factor,
            )
        }
    }

    private fun hand(
        index: List<HandPoint>,
        middle: List<HandPoint>,
        ring: List<HandPoint>,
        pinky: List<HandPoint>,
    ): List<HandPoint> = listOf(
        HandPoint(0.50f, 0.90f),
        HandPoint(0.42f, 0.78f),
        HandPoint(0.35f, 0.68f),
        HandPoint(0.30f, 0.60f),
        HandPoint(0.27f, 0.55f),
        *index.toTypedArray(),
        *middle.toTypedArray(),
        *ring.toTypedArray(),
        *pinky.toTypedArray(),
    )

    private fun extendedIndex() = listOf(
        HandPoint(0.42f, 0.62f), HandPoint(0.41f, 0.45f),
        HandPoint(0.40f, 0.30f), HandPoint(0.39f, 0.15f),
    )

    private fun extendedMiddle() = listOf(
        HandPoint(0.50f, 0.60f), HandPoint(0.50f, 0.40f),
        HandPoint(0.50f, 0.24f), HandPoint(0.50f, 0.08f),
    )

    private fun extendedRing() = listOf(
        HandPoint(0.58f, 0.62f), HandPoint(0.59f, 0.45f),
        HandPoint(0.60f, 0.30f), HandPoint(0.61f, 0.16f),
    )

    private fun extendedPinky() = listOf(
        HandPoint(0.66f, 0.66f), HandPoint(0.68f, 0.51f),
        HandPoint(0.69f, 0.39f), HandPoint(0.70f, 0.27f),
    )

    private fun curledIndex() = listOf(
        HandPoint(0.42f, 0.62f), HandPoint(0.41f, 0.50f),
        HandPoint(0.45f, 0.58f), HandPoint(0.48f, 0.66f),
    )

    private fun curledMiddle() = listOf(
        HandPoint(0.50f, 0.60f), HandPoint(0.50f, 0.48f),
        HandPoint(0.53f, 0.57f), HandPoint(0.53f, 0.66f),
    )

    private fun curledRing() = listOf(
        HandPoint(0.58f, 0.62f), HandPoint(0.59f, 0.51f),
        HandPoint(0.56f, 0.59f), HandPoint(0.55f, 0.67f),
    )

    private fun curledPinky() = listOf(
        HandPoint(0.66f, 0.66f), HandPoint(0.67f, 0.56f),
        HandPoint(0.63f, 0.62f), HandPoint(0.60f, 0.69f),
    )

    private fun distance(a: HandPoint, b: HandPoint): Float = hypot(a.x - b.x, a.y - b.y)
}
