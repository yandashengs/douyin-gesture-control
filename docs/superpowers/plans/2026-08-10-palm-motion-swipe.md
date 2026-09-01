# Palm Motion Swipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make full-open-palm up/down motion reliably emit one swipe event without depending on the strict `OPEN_PALM` pose label.

**Architecture:** `HandPoseClassifier` will expose palm-center, palm-width, and extended-finger-count features in `HandFrame`. A focused `PalmSwipeDetector` will consume those features through its own IDLE/ARMED/TRACKING/FIRED state machine, normalize displacement by palm width, tolerate short pose noise/dropouts, and return swipe events. `GestureDetector` will retain static pose voting but delegate all dynamic swipe decisions to the new detector.

**Tech Stack:** Kotlin, MediaPipe 21-point hand landmarks, JUnit 4, Gradle Android plugin.

## Global Constraints

- Do not add or train another neural-network model.
- Do not persist or upload camera frames.
- Do not modify like/comment screen coordinates in this phase.
- Dynamic swipe recognition must not require `HandPose.OPEN_PALM`.
- Keep existing `GestureEvent.SwipeUp` and `GestureEvent.SwipeDown` action contracts.
- The workspace is not a Git repository; replace commit steps with focused-test checkpoints and do not initialize Git.

---

### Task 1: Expose palm motion features

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandPoint.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandPoseClassifier.kt`
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/HandLandmarkFixtures.kt`
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/HandPoseClassifierTest.kt`

**Interfaces:**
- Produces: `HandFrame(pose, palmX, palmY, palmWidth, extendedFingerCount, pinchRatio)`.
- `palmX/palmY` are the mean coordinates of landmarks 0, 5, 9, 13, and 17.
- `palmWidth` is the distance between landmarks 5 and 17 and must be positive for a usable hand.
- `extendedFingerCount` is the count of extended index, middle, ring, and pinky fingers in `0..4`.

- [ ] **Step 1: Add failing feature tests**

Add tests with hand-derived expectations:

```kotlin
@Test
fun frameExposesPalmCenterWidthAndExtendedFingerCount() {
    val frame = classifier.classify(HandLandmarkFixtures.threeFingerPalm())

    assertEquals(HandPose.OTHER, frame.pose)
    assertEquals(0.532f, frame.palmX, 0.001f)
    assertEquals(0.680f, frame.palmY, 0.001f)
    assertEquals(0.24f, frame.palmWidth, 0.001f)
    assertEquals(3, frame.extendedFingerCount)
}

@Test
fun fistHasNoExtendedFingers() {
    assertEquals(0, classifier.classify(HandLandmarkFixtures.fist()).extendedFingerCount)
}
```

Add `threeFingerPalm()` to the fixture with index, middle, and ring extended and pinky curled. This deliberately produces `OTHER`, so later tests prove swiping is independent of the pose enum.

- [ ] **Step 2: Run the focused classifier tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*HandPoseClassifierTest' --console=plain
```

Expected: compilation fails because the new `HandFrame` fields and `threeFingerPalm()` do not exist.

- [ ] **Step 3: Implement the feature contract**

Change the data class to:

```kotlin
data class HandFrame(
    val pose: HandPose,
    val palmX: Float,
    val palmY: Float,
    val palmWidth: Float,
    val extendedFingerCount: Int,
    val pinchRatio: Float,
)
```

In `HandPoseClassifier.classify`, compute the extension booleans once, then derive:

```kotlin
val palmIndices = intArrayOf(WRIST, INDEX_MCP, MIDDLE_MCP, RING_MCP, PINKY_MCP)
val palmX = palmIndices.map { points[it].x }.average().toFloat()
val palmY = palmIndices.map { points[it].y }.average().toFloat()
val palmWidth = distance(points[INDEX_MCP], points[PINKY_MCP])
val extendedFingerCount = listOf(index, middle, ring, pinky).count { it }
```

Return those values with the existing pose and pinch ratio. For degenerate hands, return `OTHER`, the computed center, width, zero extended fingers, and infinite pinch ratio.

- [ ] **Step 4: Run classifier tests and verify GREEN**

Run the command from Step 2. Expected: all `HandPoseClassifierTest` tests pass.

---

### Task 2: Implement an independent palm swipe state machine

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/douyin/gesture/PalmSwipeDetector.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/PalmSwipeDetectorTest.kt`
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/HandLandmarkFixtures.kt`

**Interfaces:**
- Consumes: `HandFrame?` and strictly increasing `timestampMs`.
- Produces: `GestureEvent.SwipeUp`, `GestureEvent.SwipeDown`, or `GestureEvent.None` from `process(frame, timestampMs)`.
- Produces diagnostics through constructor callback `(String) -> Unit`.
- `reset()` clears all state.

- [ ] **Step 1: Add failing tests for valid trajectories**

Create real-frame tests that classify fixture landmarks before feeding the detector:

```kotlin
@Test
fun threeFingerPalmLabeledOtherCanSwipeUp() {
    val events = move(
        points = HandLandmarkFixtures.threeFingerPalm(),
        startY = 0.76f,
        endY = 0.58f,
    )
    assertEquals(1, events.count { it is GestureEvent.SwipeUp })
}

@Test
fun equivalentPalmWidthNormalizedMovesBothTrigger() {
    val normal = move(HandLandmarkFixtures.threeFingerPalm(), 0.76f, 0.58f)
    val small = move(
        HandLandmarkFixtures.scaled(HandLandmarkFixtures.threeFingerPalm(), 0.60f),
        0.70f,
        0.592f,
    )
    assertEquals(1, normal.count { it is GestureEvent.SwipeUp })
    assertEquals(1, small.count { it is GestureEvent.SwipeUp })
}
```

The test helper must use `HandPoseClassifier` and feed literal 40 ms timestamps. `scaled()` scales every point around landmark 0, preserving hand proportions.

- [ ] **Step 2: Run the new test class and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*PalmSwipeDetectorTest' --console=plain
```

Expected: compilation fails because `PalmSwipeDetector` is missing.

- [ ] **Step 3: Add failing rejection and tolerance tests before implementation**

Add tests for each user-visible failure mode:

```kotlin
@Test fun twoMissingFramesDoNotBreakSwipe()
@Test fun temporaryTwoFingerClassificationDoesNotBreakArmedSwipe()
@Test fun fistMotionDoesNotSwipe()
@Test fun horizontalPalmMotionDoesNotSwipe()
@Test fun stationaryPalmJitterDoesNotSwipe()
@Test fun unsupportedSingleFrameJumpDoesNotSwipe()
@Test fun oneMotionFiresOnlyOnceUntilReset()
@Test fun oppositeMotionAfterStillResetCanFireAgain()
```

Each assertion checks returned events, not internal state. Use literal paths: valid vertical displacement is approximately `0.75 * palmWidth`; jitter stays below `0.15 * palmWidth`; horizontal motion exceeds vertical motion; missing frames are two `null` frames at 40 ms intervals.

- [ ] **Step 4: Implement the minimal state machine**

Create `PalmSwipeDetector` with a small immutable sample and explicit state:

```kotlin
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

    fun process(frame: HandFrame?, timestampMs: Long): GestureEvent
    fun reset()
}
```

Behavior:

- IDLE enters ARMED on a usable frame with `extendedFingerCount >= 3`.
- ARMED retains samples and enters TRACKING after 120 ms of sufficient open evidence.
- TRACKING accepts noisy static pose labels and up to 160 ms of `null`; it evaluates normalized displacement using the median usable palm width.
- Trigger only after duration, vertical distance, horizontal distance, direction consistency, open-frame-ratio, and supported-step checks all pass.
- FIRED returns no further event until the palm is still for 240 ms, absent beyond tolerance, or no longer open long enough.
- Every cancellation diagnostic includes duration, normalized `dx/dy`, open ratio, and a reason; a trigger diagnostic includes the same aggregate values.

- [ ] **Step 5: Run the detector tests and verify GREEN**

Run the command from Step 2. Expected: all `PalmSwipeDetectorTest` tests pass.

---

### Task 3: Integrate dynamic swipes without disturbing static gestures

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/GestureDetector.kt`
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/GestureDetectorDynamicTest.kt`
- Modify: `app/src/test/java/com/gesturecontrol/douyin/gesture/GestureDetectorStabilityTest.kt`

**Interfaces:**
- `GestureDetector.process(points, timestampMs)` remains unchanged for callers.
- `GestureDetector` owns one `PalmSwipeDetector` and forwards classified `HandFrame?` to it.
- Dynamic swipe has priority over transition, pinch, and hold events.

- [ ] **Step 1: Replace old permissive tests with failing integration tests**

Add these assertions through the public `GestureDetector` API:

```kotlin
@Test
fun otherLabeledOpenHandSwipeWinsOverStaticEvents() {
    val events = threeFingerPalmTrajectory(GestureDetector(), 1_000L, 0.76f, 0.58f)
    assertEquals(1, events.count { it is GestureEvent.SwipeUp })
    assertFalse(events.any { it is GestureEvent.PalmHold || it is GestureEvent.PinchClose })
}

@Test
fun movingFistAndVSignDoNotSwipe() {
    val events = movePose(HandLandmarkFixtures.fist()) +
        movePose(HandLandmarkFixtures.vSign(), from = 2_000L)
    assertFalse(events.any { it is GestureEvent.SwipeUp || it is GestureEvent.SwipeDown })
}
```

Keep existing fist-then-open, pinch, hold, horizontal-motion, and one-frame-jump regression tests.

- [ ] **Step 2: Run both gesture test classes and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*GestureDetectorDynamicTest' --tests '*GestureDetectorStabilityTest' --console=plain
```

Expected: the `OTHER`-labeled three-finger palm integration test fails because the current detector filters motion through `OPEN_PALM`.

- [ ] **Step 3: Integrate `PalmSwipeDetector` and remove the old swipe implementation**

In `process`:

```kotlin
if (points == null) {
    val swipe = palmSwipeDetector.process(null, timestampMs)
    if (lastHandSeenMs != Long.MIN_VALUE &&
        timestampMs - lastHandSeenMs >= config.noHandResetMs
    ) {
        clearTrackingState()
    }
    return fireIfAllowed(swipe, timestampMs)
}

val frame = classifier.classify(points)
val swipeEvent = palmSwipeDetector.process(frame, timestampMs)
val transitionEvent = updateStablePose(timestampMs)
val pinchEvent = pinchEvent(frame, timestampMs)
val holdEvent = if (palmSwipeDetector.isTracking) GestureEvent.None else holdEvent(timestampMs)
val selected = listOf(swipeEvent, transitionEvent, pinchEvent, holdEvent)
    .firstOrNull { it !is GestureEvent.None } ?: GestureEvent.None
```

Expose read-only `isTracking` on `PalmSwipeDetector`. Remove `MotionSample`, `motionSamples`, `swipeLatched`, `swipeEvent`, and `hasUnsupportedJump` from `GestureDetector`. Keep a small independent recent-palm-center buffer only if `isPalmStill` requires it for palm-hold; it must use `frame.palmX/palmY`, not the old wrist coordinates.

Call `palmSwipeDetector.reset()` from `clearTrackingState()` and `reset()`.

- [ ] **Step 4: Run dynamic, stability, and classifier tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*GestureDetector*' --tests '*PalmSwipeDetectorTest' --tests '*HandPoseClassifierTest' --console=plain
```

Expected: all focused tests pass with no duplicate swipe, hold, or pinch events.

---

### Task 4: Full verification and device evidence

**Files:**
- Modify only if evidence requires one isolated threshold change: `app/src/main/java/com/gesturecontrol/douyin/gesture/PalmSwipeDetector.kt`
- Output: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- APK remains package `com.gesturecontrol.douyin`.
- Accessibility and foreground-camera service behavior remain unchanged.

- [ ] **Step 1: Run full automated verification**

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`; all unit tests and Lint pass.

- [ ] **Step 2: Install and restore runtime state**

```powershell
& 'D:\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r `
  'D:\game\think\shoushizhishi\app\build\outputs\apk\debug\app-debug.apk'
& 'D:\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell am start `
  -n com.gesturecontrol.douyin/.MainActivity
```

Confirm `enabled_accessibility_services` contains `DouyinGestureService`, start gesture control, and open `com.ss.android.ugc.aweme`.

- [ ] **Step 3: Capture one controlled real-device matrix**

Clear logs, then have the user perform in order: one up swipe, one down swipe, stationary open palm, horizontal open-palm move, V sign, fist. Read aggregate diagnostics and verify:

- exactly one `SwipeUp` and one `SwipeDown` recognition event;
- both are accepted by `DouyinGestureService`;
- stationary/horizontal/V/fist sequences produce no swipe;
- no volume event occurs during the matrix.

- [ ] **Step 4: If needed, change only one evidence-backed threshold and repeat**

Use the diagnostic rejection reason to select exactly one of `minVerticalPalmWidths`, `maxHorizontalPalmWidths`, `directionRatio`, or `minOpenFrameRatio`. Add or update the matching failing unit test first, verify RED, make the single config change, verify GREEN, then repeat Steps 1–3.

- [ ] **Step 5: Record checkpoint**

Report the test command results, installed APK path, device service state, and event counts. Do not claim the physical gesture issue is fixed until the controlled device matrix produces the expected recognition and action logs.
