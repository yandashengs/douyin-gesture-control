package com.gesturecontrol.douyin.gesture

import kotlin.math.acos
import kotlin.math.hypot

class HandPoseClassifier {

    fun classify(points: List<HandPoint>): HandFrame {
        require(points.size == LANDMARK_COUNT) {
            "Expected $LANDMARK_COUNT hand landmarks, got ${points.size}"
        }

        val wrist = points[WRIST]
        val palmIndices = intArrayOf(WRIST, INDEX_MCP, MIDDLE_MCP, RING_MCP, PINKY_MCP)
        val palmX = palmIndices.map { points[it].x }.average().toFloat()
        val palmY = palmIndices.map { points[it].y }.average().toFloat()
        val palmWidth = distance(points[INDEX_MCP], points[PINKY_MCP])
        val palmSize = distance(wrist, points[MIDDLE_MCP])
        if (palmSize < MIN_PALM_SIZE) {
            return HandFrame(
                HandPose.OTHER,
                palmX,
                palmY,
                palmWidth,
                0,
                Float.POSITIVE_INFINITY,
            )
        }

        val indexExt = isExtended(points, INDEX_MCP, INDEX_PIP, INDEX_DIP, INDEX_TIP, palmSize)
        val middleExt = isExtended(points, MIDDLE_MCP, MIDDLE_PIP, MIDDLE_DIP, MIDDLE_TIP, palmSize)
        val ringExt = isExtended(points, RING_MCP, RING_PIP, RING_DIP, RING_TIP, palmSize)
        val pinkyExt = isExtended(points, PINKY_MCP, PINKY_PIP, PINKY_DIP, PINKY_TIP, palmSize)
        // 弯曲判定独立于伸直：用"关节角度 OR 距离比"组合判定。
        // 单纯距离比对小指过严（小指天生短，握拳时距离比可能 1.1~1.4），
        // 加入关节角度判定后，握拳/半握时关节角度 <110° 即判弯曲，更稳定。
        val indexCurled = isCurled(points, INDEX_MCP, INDEX_PIP, INDEX_DIP, INDEX_TIP, palmSize)
        val middleCurled = isCurled(points, MIDDLE_MCP, MIDDLE_PIP, MIDDLE_DIP, MIDDLE_TIP, palmSize)
        val ringCurled = isCurled(points, RING_MCP, RING_PIP, RING_DIP, RING_TIP, palmSize)
        val pinkyCurled = isCurled(points, PINKY_MCP, PINKY_PIP, PINKY_DIP, PINKY_TIP, palmSize)
        val palmCenter = HandPoint(palmX, palmY)
        val extendedFingerCount = listOf(INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)
            .count {
                distance(points[it], palmCenter) / palmWidth.coerceAtLeast(MIN_PALM_SIZE) >=
                    MIN_OPEN_TIP_DISTANCE_PALM_WIDTHS
            }
        val pinchRatio = distance(points[THUMB_TIP], points[INDEX_TIP]) / palmSize

        val pose = when {
            pinchRatio <= OK_PINCH_RATIO && middleExt && ringExt && pinkyExt -> HandPose.OK_SIGN
            indexExt && middleExt && ringCurled && pinkyCurled -> HandPose.V_SIGN
            indexExt && middleCurled && ringCurled && pinkyExt -> HandPose.ROCK_SIGN
            indexExt && middleCurled && ringCurled && pinkyCurled -> HandPose.POINT
            indexExt && middleExt && ringExt && pinkyExt -> HandPose.OPEN_PALM
            // FIST 用"4 指都弯曲 + isClosedFist"双重判定，不依赖 !isExtended，
            // 避免 P0 放宽伸直阈值后握拳被误判为"有指伸直"而掉到 OTHER。
            indexCurled && middleCurled && ringCurled && pinkyCurled &&
                isClosedFist(points, palmSize) -> HandPose.FIST
            else -> HandPose.OTHER
        }

        return HandFrame(pose, palmX, palmY, palmWidth, extendedFingerCount, pinchRatio)
    }

    private fun isExtended(
        points: List<HandPoint>,
        mcp: Int,
        pip: Int,
        dip: Int,
        tip: Int,
        palmSize: Float,
    ): Boolean =
        angleDegrees(points[mcp], points[pip], points[dip]) >= MIN_EXTENDED_ANGLE_DEGREES &&
            angleDegrees(points[pip], points[dip], points[tip]) >= MIN_EXTENDED_ANGLE_DEGREES &&
            distance(points[WRIST], points[tip]) / palmSize >= MIN_TIP_DISTANCE_RATIO

    /**
     * 弯曲判定：关节角度 < [MAX_CURLED_ANGLE_DEGREES] **或** tip-wrist/palmSize <
     * [MAX_CURLED_TIP_DISTANCE_RATIO] 时判定为弯曲。
     *
     * 单纯距离比对小指过严（小指天生短，握拳时距离比可能 1.1~1.4），加入关节角度
     * 判定后：握拳时关节 60~90°、半握 90~120°、伸直 150~180°，110° 能稳定区分
     * 握拳/半握与伸直。两个条件取 OR，任一满足即判弯曲，对小指/无名指更宽容。
     *
     * 与 [isExtended] 独立，避免"放宽伸直阈值→握拳被误判伸直"的副作用。
     */
    private fun isCurled(
        points: List<HandPoint>,
        mcp: Int,
        pip: Int,
        dip: Int,
        tip: Int,
        palmSize: Float,
    ): Boolean {
        val curledByAngle =
            angleDegrees(points[mcp], points[pip], points[dip]) < MAX_CURLED_ANGLE_DEGREES ||
                angleDegrees(points[pip], points[dip], points[tip]) < MAX_CURLED_ANGLE_DEGREES
        val curledByDistance =
            distance(points[WRIST], points[tip]) / palmSize < MAX_CURLED_TIP_DISTANCE_RATIO
        return curledByAngle || curledByDistance
    }

    private fun isClosedFist(points: List<HandPoint>, palmSize: Float): Boolean {
        val averageTipDistance = listOf(INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)
            .map { distance(points[it], points[MIDDLE_MCP]) }
            .average()
            .toFloat()
        return averageTipDistance / palmSize <= MAX_FIST_TIP_DISTANCE_RATIO
    }

    private fun angleDegrees(a: HandPoint, vertex: HandPoint, c: HandPoint): Float {
        val abX = a.x - vertex.x
        val abY = a.y - vertex.y
        val cbX = c.x - vertex.x
        val cbY = c.y - vertex.y
        val denominator = hypot(abX, abY) * hypot(cbX, cbY)
        if (denominator < MIN_VECTOR_LENGTH) return 0f
        val cosine = ((abX * cbX + abY * cbY) / denominator).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    private fun distance(a: HandPoint, b: HandPoint): Float = hypot(a.x - b.x, a.y - b.y)

    companion object {
        private const val LANDMARK_COUNT = 21
        private const val WRIST = 0
        private const val THUMB_TIP = 4
        private const val INDEX_MCP = 5
        private const val INDEX_PIP = 6
        private const val INDEX_DIP = 7
        private const val INDEX_TIP = 8
        private const val MIDDLE_MCP = 9
        private const val MIDDLE_PIP = 10
        private const val MIDDLE_DIP = 11
        private const val MIDDLE_TIP = 12
        private const val RING_MCP = 13
        private const val RING_PIP = 14
        private const val RING_DIP = 15
        private const val RING_TIP = 16
        private const val PINKY_MCP = 17
        private const val PINKY_PIP = 18
        private const val PINKY_DIP = 19
        private const val PINKY_TIP = 20

        private const val MIN_PALM_SIZE = 0.0001f
        private const val MIN_VECTOR_LENGTH = 0.000001f
        // 放宽伸直判定阈值：自然伸直的手指关节角度通常 150°~165°，但侧手/稍弯曲/
        // 远距离时关键点会算出 140°~150°。150° 过严导致 V 字/OK/开掌频繁掉到 OTHER。
        private const val MIN_EXTENDED_ANGLE_DEGREES = 140f
        // 放宽指尖-手腕距离比：原 1.5 对小指过严（小指天生短，开掌时距离比可能 1.3~1.5），
        // 降到 1.3 让 OPEN_PALM 更稳定，PalmHold/FistThenOpen 更易触发。
        // 与 isCurled 的距离阈值 1.3 不重叠（isExtended ≥1.3，isCurled <1.3）。
        private const val MIN_TIP_DISTANCE_RATIO = 1.3f
        private const val MAX_FIST_TIP_DISTANCE_RATIO = 1.25f
        // 弯曲判定：关节角度 < 120° 判为弯曲。
        // 握拳 60~90°、半握 90~120°、伸直 150~180°。
        // 120° 比 110° 更宽容，覆盖 V 字时 ring/pinky 关节抖动到 110~120° 的情况，
        // 减少 isCurled 抖动导致的 V_SIGN 失败。
        private const val MAX_CURLED_ANGLE_DEGREES = 120f
        // 弯曲判定：tip-wrist/palmSize < 1.3 判为弯曲（与角度判定取 OR）。
        // 握拳 ~0.8、半握 ~1.0、伸直 ~2.5。1.3 对小指更宽容（小指天生短）。
        private const val MAX_CURLED_TIP_DISTANCE_RATIO = 1.3f
        // OK 手势的 pinch 阈值：实际 OK 手势 pinchRatio 常在 0.20~0.35，
        // 0.25 过严导致很多 OK 手势掉到 OTHER。0.35 覆盖正常 OK 手势区间。
        // Pinch 音量阈值（pinchClosedRatio=0.30）仍低于此值，OK 优先级在前不受影响。
        private const val OK_PINCH_RATIO = 0.35f
        private const val MIN_OPEN_TIP_DISTANCE_PALM_WIDTHS = 0.90f
    }
}
