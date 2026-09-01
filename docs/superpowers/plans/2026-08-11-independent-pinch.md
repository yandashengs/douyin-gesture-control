# Independent Pinch Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recognize thumb-index volume gestures even when the static pose is POINT, without changing other gesture classifiers.

**Architecture:** Add a pose-independent `PinchGestureDetector` that recognizes confirmed ratio transitions. Integrate it at the existing pinch event boundary and defer only PointHold while a transition is pending.

**Tech Stack:** Kotlin, JUnit 4, Gradle Android plugin.

## Global Constraints

- Do not modify `HandPoseClassifier`, `PalmSwipeDetector`, mappings, or action execution.
- Keep thresholds `closed=0.30`, `open=0.55`.
- Initial state emits no event; only confirmed transitions emit.
- Workspace is not a Git repository, so test checkpoints replace commits.

---

### Task 1: Pinch transition state machine

**Files:**
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/PinchGestureDetectorTest.kt`
- Create: `app/src/main/java/com/gesturecontrol/douyin/gesture/PinchGestureDetector.kt`

**Interfaces:**
- `process(frame: HandFrame?, timestampMs: Long): GestureEvent`
- `isTransitionPending: Boolean`
- `reset()`

- [ ] Add failing tests for POINT-classified OPEN→CLOSED and CLOSED→OPEN transitions, initial-state silence, threshold jitter, and missing-hand reset.
- [ ] Run `:app:testDebugUnitTest --tests '*PinchGestureDetectorTest'` and verify failure because the class is absent.
- [ ] Implement the two-threshold state machine with 120ms confirmation and no pose dependency.
- [ ] Re-run the focused test and verify all cases pass.

### Task 2: GestureDetector integration and isolation

**Files:**
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/GestureDetectorDynamicTest.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/GestureDetector.kt`

**Interfaces:**
- Replace private `pinchEvent` behavior with `pinchGestureDetector.process(frame, now)`.
- Suppress only `PointHold` when `isTransitionPending` is true.

- [ ] Add failing public-API tests: quick POINT pinch emits volume event; static POINT still emits PointHold; active pinch does not emit PointHold.
- [ ] Run GestureDetector dynamic/stability tests and verify the quick POINT pinch test fails against current code.
- [ ] Integrate the detector, remove old pinch state fields/method, and reset it with other tracking state.
- [ ] Run all gesture-focused tests and verify pass.

### Task 3: Full verification and installation

- [ ] Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- [ ] Install the debug APK, preserve/restore accessibility, start recognition, and open Douyin.
- [ ] Verify logs for one close and one expand gesture before claiming physical success.
