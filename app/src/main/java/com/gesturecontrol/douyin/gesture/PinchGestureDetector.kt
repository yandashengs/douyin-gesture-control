package com.gesturecontrol.douyin.gesture

/** Recognizes thumb-index distance transitions without depending on static hand poses. */
class PinchGestureDetector(
    private val config: Config = Config(),
    private val onDiagnostic: (String) -> Unit = {},
) {
    data class Config(
        val closedRatio: Float = 0.30f,
        val openRatio: Float = 0.55f,
        val confirmMs: Long = 120L,
        val noHandResetMs: Long = 250L,
    )

    private enum class State { UNKNOWN, OPEN, CLOSED }

    private var confirmedState = State.UNKNOWN
    private var confirmedFingerCount = -1
    private var pendingState = State.UNKNOWN
    private var pendingFingerCount = -1
    private var pendingSinceMs = 0L
    private var lastSeenMs = Long.MIN_VALUE
    private var lastTimestampMs = Long.MIN_VALUE

    val isTransitionPending: Boolean
        get() = pendingState != State.UNKNOWN && pendingState != confirmedState

    fun process(frame: HandFrame?, timestampMs: Long): GestureEvent {
        if (timestampMs <= lastTimestampMs) return GestureEvent.None
        lastTimestampMs = timestampMs

        if (frame == null || !frame.pinchRatio.isFinite()) {
            if (lastSeenMs != Long.MIN_VALUE && timestampMs - lastSeenMs >= config.noHandResetMs) {
                clearState()
            }
            return GestureEvent.None
        }
        lastSeenMs = timestampMs

        val proposed = when {
            frame.pinchRatio <= config.closedRatio -> State.CLOSED
            frame.pinchRatio >= config.openRatio -> State.OPEN
            else -> {
                clearPending()
                return GestureEvent.None
            }
        }

        if (proposed == confirmedState) {
            confirmedFingerCount = frame.extendedFingerCount
            clearPending()
            return GestureEvent.None
        }
        if (proposed != pendingState || frame.extendedFingerCount != pendingFingerCount) {
            pendingState = proposed
            pendingFingerCount = frame.extendedFingerCount
            pendingSinceMs = timestampMs
            return GestureEvent.None
        }
        if (timestampMs - pendingSinceMs < config.confirmMs) return GestureEvent.None

        val previous = confirmedState
        val sameNonThumbShape = confirmedFingerCount < 0 ||
            confirmedFingerCount == pendingFingerCount
        confirmedState = proposed
        confirmedFingerCount = pendingFingerCount
        clearPending()
        if (previous == State.UNKNOWN || !sameNonThumbShape) {
            onDiagnostic("捏合基线: ${proposed.name}, ratio=${frame.pinchRatio}")
            return GestureEvent.None
        }

        val event = when {
            previous == State.OPEN && proposed == State.CLOSED -> GestureEvent.PinchClose
            previous == State.CLOSED && proposed == State.OPEN -> GestureEvent.PinchExpand
            else -> GestureEvent.None
        }
        if (event !is GestureEvent.None) {
            onDiagnostic(
                "捏合状态: ${previous.name} -> ${proposed.name}, ratio=${frame.pinchRatio}"
            )
        }
        return event
    }

    private fun clearState() {
        confirmedState = State.UNKNOWN
        confirmedFingerCount = -1
        clearPending()
        lastSeenMs = Long.MIN_VALUE
    }

    private fun clearPending() {
        pendingState = State.UNKNOWN
        pendingFingerCount = -1
        pendingSinceMs = 0L
    }

    fun reset() {
        clearState()
        lastTimestampMs = Long.MIN_VALUE
    }
}
