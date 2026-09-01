package com.gesturecontrol.douyin.gesture

import java.util.ArrayDeque
import kotlin.math.hypot

class GestureDetector(
    private val config: Config = Config(),
    private val classifier: HandPoseClassifier = HandPoseClassifier(),
    private val onDiagnostic: (String) -> Unit = {},
) {

    data class Config(
        val voteWindowMs: Long = 400L,
        val minVoteFrames: Int = 5,
        val voteRatio: Float = 0.70f,
        val poseConfirmMs: Long = 180L,
        val noHandResetMs: Long = 250L,
        // 缩短冷却时间：原 650ms 会吞掉连续动作，降到 400ms 让连滑/连点更跟手
        val globalCooldownMs: Long = 400L,
        // 缩短各姿势保持时长：原 800/1200/700ms 要求姿势几乎静止过久，实际很难凑够。
        // 降到 500/700ms 仍能防误触，但正常动作能稳定触发。
        val vHoldMs: Long = 500L,
        val palmHoldMs: Long = 700L,
        val pointHoldMs: Long = 500L,
        val okHoldMs: Long = 500L,
        val rockHoldMs: Long = 500L,
        val stillWindowMs: Long = 300L,
        // 放宽手掌静止判定：原 0.035（480p 下约 17 像素）过严，手臂悬空几乎必然超过。
        // 0.07 约 34 像素，允许轻微晃动仍判定为"保持"。
        val stillDisplacement: Float = 0.07f,
        val fistToOpenWindowMs: Long = 900L,
    )

    private data class PoseSample(val timeMs: Long, val pose: HandPose)
    private data class PalmPositionSample(
        val timeMs: Long,
        val x: Float,
        val y: Float,
        val openPalm: Boolean,
    )

    private val poseSamples = ArrayDeque<PoseSample>()
    private val palmPositionSamples = ArrayDeque<PalmPositionSample>()
    private val palmSwipeDetector = PalmSwipeDetector(onDiagnostic = onDiagnostic)
    private val pinchGestureDetector = PinchGestureDetector(onDiagnostic = onDiagnostic)

    private var lastTimestampMs = Long.MIN_VALUE
    private var lastHandSeenMs = Long.MIN_VALUE
    private var pendingPose = HandPose.NONE
    private var pendingPoseSinceMs = 0L
    private var stablePose = HandPose.NONE
    private var stableSinceMs = 0L
    private var holdFired = false
    private var lastEventTimeMs = Long.MIN_VALUE
    private var fistStableAtMs: Long? = null
    fun process(points: List<HandPoint>?, timestampMs: Long): GestureEvent {
        if (timestampMs <= lastTimestampMs) return GestureEvent.None
        lastTimestampMs = timestampMs

        if (points == null) {
            val swipeEvent = palmSwipeDetector.process(null, timestampMs)
            pinchGestureDetector.process(null, timestampMs)
            if (lastHandSeenMs != Long.MIN_VALUE &&
                timestampMs - lastHandSeenMs >= config.noHandResetMs
            ) {
                clearTrackingState()
            }
            return fireIfAllowed(swipeEvent, timestampMs)
        }

        lastHandSeenMs = timestampMs
        val frame = classifier.classify(points)
        addPoseSample(frame.pose, timestampMs)
        addPalmPositionSample(frame, timestampMs)
        val swipeEvent = palmSwipeDetector.process(frame, timestampMs)
        val transitionEvent = updateStablePose(timestampMs)
        val pinchEvent = pinchGestureDetector.process(frame, timestampMs)
        val holdEvent = if (
            stablePose == HandPose.POINT && pinchGestureDetector.isTransitionPending
        ) {
            GestureEvent.None
        } else {
            holdEvent(timestampMs)
        }

        val selected = listOf(swipeEvent, transitionEvent, pinchEvent, holdEvent)
            .firstOrNull { it !is GestureEvent.None }
            ?: GestureEvent.None
        return fireIfAllowed(selected, timestampMs)
    }

    private fun addPoseSample(pose: HandPose, now: Long) {
        poseSamples.addLast(PoseSample(now, pose))
        while (poseSamples.isNotEmpty() && now - poseSamples.first.timeMs > config.voteWindowMs) {
            poseSamples.removeFirst()
        }
    }

    private fun addPalmPositionSample(frame: HandFrame, now: Long) {
        val belongsToOpenPalm = frame.pose == HandPose.OPEN_PALM || stablePose == HandPose.OPEN_PALM
        palmPositionSamples.addLast(
            PalmPositionSample(now, frame.palmX, frame.palmY, belongsToOpenPalm)
        )
        val keepMs = maxOf(config.voteWindowMs, config.stillWindowMs)
        while (palmPositionSamples.isNotEmpty() &&
            now - palmPositionSamples.first.timeMs > keepMs
        ) {
            palmPositionSamples.removeFirst()
        }
    }

    private fun updateStablePose(now: Long): GestureEvent {
        val dominant = dominantPose()
        if (dominant != pendingPose) {
            pendingPose = dominant
            pendingPoseSinceMs = now
            onDiagnostic("候选姿势: ${dominant.name}")
            return GestureEvent.None
        }

        if (dominant != HandPose.NONE &&
            dominant != stablePose &&
            now - pendingPoseSinceMs >= config.poseConfirmMs
        ) {
            val previous = stablePose
            stablePose = dominant
            stableSinceMs = now
            holdFired = false
            onDiagnostic("稳定姿势: ${previous.name} -> ${stablePose.name}")
            return onStablePoseChanged(previous, stablePose, now)
        }
        return GestureEvent.None
    }

    private fun onStablePoseChanged(
        previous: HandPose,
        current: HandPose,
        now: Long,
    ): GestureEvent {
        if (current == HandPose.FIST) {
            fistStableAtMs = now
            return GestureEvent.None
        }

        val fistAt = fistStableAtMs
        if (current == HandPose.OPEN_PALM && fistAt != null) {
            fistStableAtMs = null
            return if (now - fistAt <= config.fistToOpenWindowMs) {
                onDiagnostic("稳定握拳张开: ${now - fistAt}ms")
                GestureEvent.FistThenOpen
            } else {
                GestureEvent.None
            }
        }

        if (current != HandPose.NONE && previous == HandPose.FIST) {
            fistStableAtMs = null
        }
        return GestureEvent.None
    }

    private fun dominantPose(): HandPose {
        if (poseSamples.size < config.minVoteFrames) return HandPose.NONE
        val counts = poseSamples.groupingBy { it.pose }.eachCount()
        val dominant = counts.maxByOrNull { it.value } ?: return HandPose.NONE
        val ratio = dominant.value.toFloat() / poseSamples.size
        return if (ratio >= config.voteRatio) dominant.key else HandPose.NONE
    }

    private fun holdEvent(now: Long): GestureEvent {
        if (holdFired) return GestureEvent.None
        val elapsed = now - stableSinceMs
        val event = when (stablePose) {
            HandPose.V_SIGN -> if (elapsed >= config.vHoldMs) GestureEvent.VSignHold else null
            HandPose.OPEN_PALM -> if (
                elapsed >= config.palmHoldMs && isPalmStill(now)
            ) GestureEvent.PalmHold else null
            HandPose.POINT -> if (elapsed >= config.pointHoldMs) GestureEvent.PointHold else null
            HandPose.OK_SIGN -> if (elapsed >= config.okHoldMs) GestureEvent.OkSign else null
            HandPose.ROCK_SIGN -> if (elapsed >= config.rockHoldMs) GestureEvent.RockSign else null
            else -> null
        } ?: return GestureEvent.None

        return event
    }

    private fun isPalmStill(now: Long): Boolean {
        val recent = palmPositionSamples.filter {
            it.openPalm && now - it.timeMs <= config.stillWindowMs
        }
        if (recent.size < 2) return false
        val xRange = recent.maxOf { it.x } - recent.minOf { it.x }
        val yRange = recent.maxOf { it.y } - recent.minOf { it.y }
        return hypot(xRange, yRange) <= config.stillDisplacement
    }

    private fun fireIfAllowed(event: GestureEvent, now: Long): GestureEvent {
        if (event is GestureEvent.None) return event
        val cooledDown = lastEventTimeMs == Long.MIN_VALUE ||
            now - lastEventTimeMs >= config.globalCooldownMs
        if (!cooledDown) return GestureEvent.None

        holdFired = true
        lastEventTimeMs = now
        onDiagnostic("触发事件: ${event.label}")
        return event
    }

    private fun clearTrackingState() {
        poseSamples.clear()
        palmPositionSamples.clear()
        palmSwipeDetector.reset()
        pinchGestureDetector.reset()
        pendingPose = HandPose.NONE
        pendingPoseSinceMs = 0L
        stablePose = HandPose.NONE
        stableSinceMs = 0L
        holdFired = false
        lastHandSeenMs = Long.MIN_VALUE
        fistStableAtMs = null
    }

    fun reset() {
        clearTrackingState()
        lastTimestampMs = Long.MIN_VALUE
        lastEventTimeMs = Long.MIN_VALUE
    }
}
