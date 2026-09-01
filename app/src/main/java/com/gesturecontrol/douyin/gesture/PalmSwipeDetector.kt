package com.gesturecontrol.douyin.gesture

import java.util.ArrayDeque
import kotlin.math.abs

/** Detects full-palm vertical motion independently from strict static pose labels. */
class PalmSwipeDetector(
    private val config: Config = Config(),
    private val onDiagnostic: (String) -> Unit = {},
) {
    data class Config(
        val armConfirmMs: Long = 120L,
        val dropoutToleranceMs: Long = 160L,
        val maxTrackMs: Long = 700L,
        val minTrackMs: Long = 160L,
        val minVerticalPalmWidths: Float = 0.55f,
        val maxHorizontalPalmWidths: Float = 0.55f,
        val directionRatio: Float = 0.65f,
        val minOpenFingerCount: Int = 3,
        val minOpenFrameRatio: Float = 0.45f,
        val maxSingleStepPalmWidths: Float = 0.45f,
        val resetStillMs: Long = 240L,
        val resetStillPalmWidths: Float = 0.15f,
    )

    private enum class State { IDLE, ARMED, TRACKING, FIRED }

    private data class Sample(
        val timeMs: Long,
        val x: Float,
        val y: Float,
        val palmWidth: Float,
        val open: Boolean,
    )

    private val samples = ArrayDeque<Sample>()
    private val resetSamples = ArrayDeque<Sample>()
    private var state = State.IDLE
    private var armedAtMs = Long.MIN_VALUE
    private var lastSeenMs = Long.MIN_VALUE
    private var lastOpenMs = Long.MIN_VALUE
    private var lastTimestampMs = Long.MIN_VALUE
    private var lastIdleDiagnosticMs = Long.MIN_VALUE
    private var lastTrackDiagnosticMs = Long.MIN_VALUE

    val isTracking: Boolean
        get() = state == State.ARMED || state == State.TRACKING

    fun process(frame: HandFrame?, timestampMs: Long): GestureEvent {
        if (timestampMs <= lastTimestampMs) return GestureEvent.None
        lastTimestampMs = timestampMs

        if (frame == null || frame.palmWidth <= MIN_USABLE_PALM_WIDTH) {
            handleMissing(timestampMs)
            return GestureEvent.None
        }

        lastSeenMs = timestampMs
        val sample = Sample(
            timeMs = timestampMs,
            x = frame.palmX,
            y = frame.palmY,
            palmWidth = frame.palmWidth,
            open = frame.extendedFingerCount >= config.minOpenFingerCount,
        )
        if (sample.open) lastOpenMs = timestampMs

        return when (state) {
            State.IDLE -> {
                if (sample.open) {
                    beginArming(sample)
                } else if (lastIdleDiagnosticMs == Long.MIN_VALUE ||
                    timestampMs - lastIdleDiagnosticMs >= DIAGNOSTIC_INTERVAL_MS
                ) {
                    lastIdleDiagnosticMs = timestampMs
                    onDiagnostic(
                        "掌心未武装: openFingers=${frame.extendedFingerCount}, " +
                            "palmWidth=${frame.palmWidth}"
                    )
                }
                GestureEvent.None
            }
            State.ARMED -> processArmed(sample)
            State.TRACKING -> processTracking(sample)
            State.FIRED -> processFired(sample)
        }
    }

    private fun processArmed(sample: Sample): GestureEvent {
        samples.addLast(sample)
        if (sample.timeMs - lastOpenMs > config.dropoutToleranceMs) {
            cancel("open-evidence-lost")
            return GestureEvent.None
        }
        if (sample.timeMs - armedAtMs >= config.armConfirmMs) {
            state = State.TRACKING
            onDiagnostic("掌心轨迹跟踪: armedFor=${sample.timeMs - armedAtMs}ms")
            return evaluateSwipe()
        }
        return GestureEvent.None
    }

    private fun processTracking(sample: Sample): GestureEvent {
        samples.addLast(sample)
        while (samples.isNotEmpty() && sample.timeMs - samples.first.timeMs > config.maxTrackMs) {
            samples.removeFirst()
        }
        return evaluateSwipe()
    }

    private fun evaluateSwipe(): GestureEvent {
        if (samples.size < 3) return GestureEvent.None
        val first = samples.first
        val last = samples.last
        val duration = last.timeMs - first.timeMs
        if (duration < config.minTrackMs) return GestureEvent.None

        val palmWidth = medianPalmWidth(samples)
        val dx = (last.x - first.x) / palmWidth
        val dy = (last.y - first.y) / palmWidth
        val openRatio = samples.count { it.open }.toFloat() / samples.size
        if (lastTrackDiagnosticMs == Long.MIN_VALUE ||
            last.timeMs - lastTrackDiagnosticMs >= DIAGNOSTIC_INTERVAL_MS
        ) {
            lastTrackDiagnosticMs = last.timeMs
            onDiagnostic(
                "掌心轨迹候选: dyPalm=$dy, dxPalm=$dx, duration=${duration}ms, " +
                    "openRatio=$openRatio"
            )
        }
        if (abs(dy) < config.minVerticalPalmWidths ||
            abs(dx) > config.maxHorizontalPalmWidths ||
            openRatio < config.minOpenFrameRatio
        ) return GestureEvent.None

        val steps = samples.zipWithNext { a, b -> (b.y - a.y) / palmWidth }
        val meaningful = steps.filter { abs(it) >= MIN_MEANINGFUL_STEP_PALM_WIDTHS }
        if (meaningful.isEmpty()) return GestureEvent.None
        val direction = if (dy < 0f) -1 else 1
        val agreeing = meaningful.count { (it < 0f) == (direction < 0) }
        val consistency = agreeing.toFloat() / meaningful.size
        if (consistency < config.directionRatio || hasUnsupportedJump(steps)) {
            return GestureEvent.None
        }

        val event = if (dy < 0f) GestureEvent.SwipeUp else GestureEvent.SwipeDown
        state = State.FIRED
        resetSamples.clear()
        resetSamples.addLast(last)
        onDiagnostic(
            "掌心轨迹触发: event=${event.label}, dyPalm=$dy, dxPalm=$dx, " +
                "duration=${duration}ms, consistency=$consistency, openRatio=$openRatio"
        )
        return event
    }

    private fun hasUnsupportedJump(steps: List<Float>): Boolean {
        steps.forEachIndexed { index, step ->
            if (abs(step) <= config.maxSingleStepPalmWidths) return@forEachIndexed
            val following = steps.drop(index + 1).take(2)
            val supported = following.size == 2 && following.all {
                abs(it) >= MIN_MEANINGFUL_STEP_PALM_WIDTHS && (it < 0f) == (step < 0f)
            }
            if (!supported) return true
        }
        return false
    }

    private fun processFired(sample: Sample): GestureEvent {
        resetSamples.addLast(sample)
        while (resetSamples.isNotEmpty() &&
            sample.timeMs - resetSamples.first.timeMs > config.resetStillMs
        ) {
            resetSamples.removeFirst()
        }

        if (!sample.open && sample.timeMs - lastOpenMs > config.dropoutToleranceMs) {
            clearToIdle()
            return GestureEvent.None
        }

        if (resetSamples.size >= 2 &&
            resetSamples.last.timeMs - resetSamples.first.timeMs >= config.resetStillMs - FRAME_TOLERANCE_MS
        ) {
            val width = medianPalmWidth(resetSamples)
            val xRange = (resetSamples.maxOf { it.x } - resetSamples.minOf { it.x }) / width
            val yRange = (resetSamples.maxOf { it.y } - resetSamples.minOf { it.y }) / width
            if (maxOf(xRange, yRange) <= config.resetStillPalmWidths) {
                clearToIdle()
                if (sample.open) beginArming(sample)
            }
        }
        return GestureEvent.None
    }

    private fun handleMissing(now: Long) {
        if (lastSeenMs == Long.MIN_VALUE || now - lastSeenMs <= config.dropoutToleranceMs) return
        if (state != State.IDLE) cancel("hand-lost")
    }

    private fun beginArming(sample: Sample) {
        state = State.ARMED
        armedAtMs = sample.timeMs
        samples.clear()
        samples.addLast(sample)
        resetSamples.clear()
        onDiagnostic(
            "掌心轨迹武装: openFingers>=${config.minOpenFingerCount}, " +
                "palmWidth=${sample.palmWidth}"
        )
    }

    private fun cancel(reason: String) {
        if (samples.isNotEmpty()) {
            val first = samples.first
            val last = samples.last
            val width = medianPalmWidth(samples)
            val openRatio = samples.count { it.open }.toFloat() / samples.size
            onDiagnostic(
                "掌心轨迹取消: reason=$reason, dyPalm=${(last.y - first.y) / width}, " +
                    "dxPalm=${(last.x - first.x) / width}, " +
                    "duration=${last.timeMs - first.timeMs}ms, openRatio=$openRatio"
            )
        }
        clearToIdle()
    }

    private fun clearToIdle() {
        state = State.IDLE
        armedAtMs = Long.MIN_VALUE
        samples.clear()
        resetSamples.clear()
    }

    private fun medianPalmWidth(values: Collection<Sample>): Float {
        val widths = values.map { it.palmWidth }.sorted()
        return widths[widths.size / 2].coerceAtLeast(MIN_USABLE_PALM_WIDTH)
    }

    fun reset() {
        clearToIdle()
        lastSeenMs = Long.MIN_VALUE
        lastOpenMs = Long.MIN_VALUE
        lastTimestampMs = Long.MIN_VALUE
        lastIdleDiagnosticMs = Long.MIN_VALUE
        lastTrackDiagnosticMs = Long.MIN_VALUE
    }

    companion object {
        private const val MIN_USABLE_PALM_WIDTH = 0.001f
        private const val MIN_MEANINGFUL_STEP_PALM_WIDTHS = 0.015f
        private const val FRAME_TOLERANCE_MS = 40L
        private const val DIAGNOSTIC_INTERVAL_MS = 500L
    }
}

private inline fun <T, R> Iterable<T>.zipWithNext(transform: (T, T) -> R): List<R> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()
    val result = mutableListOf<R>()
    var previous = iterator.next()
    while (iterator.hasNext()) {
        val current = iterator.next()
        result += transform(previous, current)
        previous = current
    }
    return result
}
