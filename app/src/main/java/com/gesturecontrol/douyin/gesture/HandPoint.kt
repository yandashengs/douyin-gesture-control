package com.gesturecontrol.douyin.gesture

data class HandPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

enum class HandPose {
    NONE,
    OPEN_PALM,
    FIST,
    V_SIGN,
    POINT,
    OK_SIGN,
    ROCK_SIGN,
    OTHER,
}

data class HandFrame(
    val pose: HandPose,
    val palmX: Float,
    val palmY: Float,
    val palmWidth: Float,
    val extendedFingerCount: Int,
    val pinchRatio: Float,
)
