# Gesture Recognition Accuracy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all seven default gestures and three configurable poses resistant to missed detections, one-frame noise, cross-classification, and repeated triggering.

**Architecture:** Keep CameraX and the existing hand landmark model, but run MediaPipe in VIDEO mode at a requested 1280×720 analysis resolution. Convert MediaPipe landmarks into pure Kotlin `HandPoint` values, classify each frame with normalized geometry, then pass candidates through temporal voting, gesture-specific state machines, and priority arbitration before producing `GestureEvent`.

**Tech Stack:** Kotlin 1.9.24, Android SDK 34, CameraX 1.3.4, MediaPipe Tasks Vision 0.10.14, JUnit 4.13.2, Gradle 8.7.

## Global Constraints

- Preserve every existing `GestureEvent` and mapping identifier.
- Keep `hand_landmarker.task`; do not add a Gesture Recognizer model or cloud processing.
- Request 1280×720 but retain CameraX fallback behavior when the exact size is unavailable.
- Use monotonic milliseconds for both MediaPipe VIDEO calls and GestureDetector timing.
- A frame may produce at most one event, using the design document's priority order.
- The workspace is not a Git repository, so commit and worktree steps are omitted.

---

### Task 1: Pure landmark model and normalized pose classifier

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandPoint.kt`
- Create: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandPoseClassifier.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/HandLandmarkFixtures.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/HandPoseClassifierTest.kt`

**Interfaces:**
- Produces: `data class HandPoint(val x: Float, val y: Float, val z: Float = 0f)`.
- Produces: `enum class HandPose { NONE, OPEN_PALM, FIST, V_SIGN, POINT, OK_SIGN, ROCK_SIGN, OTHER }`.
- Produces: `data class HandFrame(val pose: HandPose, val wristX: Float, val wristY: Float, val pinchRatio: Float)`.
- Produces: `HandPoseClassifier.classify(points: List<HandPoint>): HandFrame`.

- [ ] **Step 1: Add representative landmark fixtures**

Create `HandLandmarkFixtures` with literal 21-point fixtures. Use a neutral wrist `(0.50, 0.90)`, MCP row around `y=0.62`, extended joints progressing upward, and curled joints folding back toward the palm. Provide these functions:

```kotlin
fun openPalm(): List<HandPoint>
fun fist(): List<HandPoint>
fun vSign(): List<HandPoint>
fun point(): List<HandPoint>
fun okSign(): List<HandPoint>
fun rockSign(): List<HandPoint>
fun other(): List<HandPoint>
fun withWrist(points: List<HandPoint>, x: Float, y: Float): List<HandPoint>
fun withPinchRatio(points: List<HandPoint>, ratio: Float): List<HandPoint>
```

`withWrist` must translate every point by the wrist delta. `withPinchRatio` must position thumb tip index 4 relative to index tip 8 by `ratio * palmSize`, where palmSize is distance from indices 0 to 9.

- [ ] **Step 2: Write classifier tests before production types exist**

```kotlin
class HandPoseClassifierTest {
    private val classifier = HandPoseClassifier()

    @Test fun classifiesOpenPalm() = assertPose(HandPose.OPEN_PALM, HandLandmarkFixtures.openPalm())
    @Test fun classifiesFist() = assertPose(HandPose.FIST, HandLandmarkFixtures.fist())
    @Test fun classifiesVSign() = assertPose(HandPose.V_SIGN, HandLandmarkFixtures.vSign())
    @Test fun classifiesPoint() = assertPose(HandPose.POINT, HandLandmarkFixtures.point())
    @Test fun classifiesOkBeforeGenericMasks() = assertPose(HandPose.OK_SIGN, HandLandmarkFixtures.okSign())
    @Test fun classifiesRockSign() = assertPose(HandPose.ROCK_SIGN, HandLandmarkFixtures.rockSign())
    @Test fun classifiesAmbiguousShapeAsOther() = assertPose(HandPose.OTHER, HandLandmarkFixtures.other())

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteLandmarkList() { classifier.classify(emptyList()) }

    private fun assertPose(expected: HandPose, points: List<HandPoint>) {
        assertEquals(expected, classifier.classify(points).pose)
    }
}
```

- [ ] **Step 3: Run focused classifier tests and verify RED**

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*HandPoseClassifierTest' --console=plain
```

Expected: compilation fails because `HandPoint`, `HandPose`, and `HandPoseClassifier` do not exist.

- [ ] **Step 4: Implement normalized geometry**

`HandPoseClassifier` must:

- require exactly 21 points;
- calculate `palmSize = distance(points[0], points[9])` and reject sizes below `0.0001f` as OTHER;
- calculate PIP and DIP angles with a clamped dot-product cosine;
- mark a finger extended only when both angles are at least 150 degrees and tip-to-wrist distance is at least 1.65 palm lengths;
- calculate `pinchRatio = distance(points[4], points[8]) / palmSize`;
- classify in this exact order: OK (`pinchRatio <= 0.32` and middle/ring/pinky extended), V, ROCK, POINT, OPEN_PALM, FIST, OTHER;
- require FIST to have all four fingers unextended and average tip-to-point-9 distance no greater than 1.25 palm lengths.

- [ ] **Step 5: Run focused tests and adjust fixtures only for geometric correctness**

Run Step 3. Expected: all eight classifier tests pass. Do not change production thresholds merely to compensate for an internally inconsistent fixture; inspect angles and normalized distances first.

---

### Task 2: Temporal voting and one-shot hold episodes

**Files:**
- Replace: `app/src/main/java/com/gesturecontrol/douyin/gesture/GestureDetector.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/GestureDetectorStabilityTest.kt`

**Interfaces:**
- Consumes: `HandPoseClassifier.classify(points): HandFrame`.
- Preserves: `GestureDetector.process(landmarks, timestampMs): GestureEvent`, changing the landmark type to `List<HandPoint>?`.
- Preserves: `GestureDetector.reset()`.
- Produces diagnostics through constructor callback `onDiagnostic: (String) -> Unit = {}`.

- [ ] **Step 1: Write a timed feed helper and stability tests**

The test helper feeds frames every 40ms:

```kotlin
private fun feed(
    detector: GestureDetector,
    points: List<HandPoint>?,
    from: Long,
    duration: Long,
): List<GestureEvent> = buildList {
    var time = from
    while (time <= from + duration) {
        add(detector.process(points, time))
        time += 40L
    }
}
```

Tests must prove:

```kotlin
@Test fun oneWrongFrameDoesNotCreateFistThenOpen()
@Test fun vSignHoldFiresOncePerEpisode()
@Test fun leavingAndReturningAllowsSecondVSignHold()
@Test fun palmHoldRequiresStableLowMotionPalm()
@Test fun noHandFor250MsResetsPendingAndStableState()
@Test fun repeatedTimestampDoesNotCorruptState()
```

Use literal timelines: establish open palm for 500ms, inject one fist frame, return to open for 500ms, and assert no `FistThenOpen`; hold V for 1600ms and assert exactly one `VSignHold`; feed OTHER for 400ms before a second V episode and assert a second event.

- [ ] **Step 2: Run stability tests and verify RED against the current detector**

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*GestureDetectorStabilityTest' --console=plain
```

Expected: compilation fails because current GestureDetector consumes MediaPipe landmarks and lacks stable episode behavior.

- [ ] **Step 3: Implement the 400ms pose vote window**

Implement these config defaults:

```kotlin
val voteWindowMs = 400L
val minVoteFrames = 5
val voteRatio = 0.70f
val poseConfirmMs = 180L
val noHandResetMs = 250L
val globalCooldownMs = 650L
val vHoldMs = 800L
val palmHoldMs = 1200L
val pointHoldMs = 700L
val okHoldMs = 700L
val rockHoldMs = 700L
```

Keep timestamp ordering monotonic by ignoring any frame whose timestamp is not greater than the previous processed timestamp. Prune pose samples older than 400ms. A dominant pose may become stable only with five samples, at least 70% share, and the same dominant candidate lasting 180ms.

- [ ] **Step 4: Implement stable-pose episodes**

On stable pose change, reset `stableSince` and the episode's `holdFired`. V, palm, point, OK, and rock holds fire at their configured duration once per episode. Palm hold is eligible only when the motion window's displacement during the last 300ms is below 0.035 normalized units. Record candidate and stable transitions through `onDiagnostic` only when values change.

- [ ] **Step 5: Run stability tests and verify GREEN**

Run Step 2. Expected: all six stability tests pass.

---

### Task 3: Windowed swipes, stable fist-open, and pinch hysteresis

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/GestureDetector.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/GestureDetectorDynamicTest.kt`

**Interfaces:**
- Consumes the stable pose state from Task 2.
- Produces existing `SwipeUp`, `SwipeDown`, `FistThenOpen`, `PinchClose`, and `PinchExpand` events.

- [ ] **Step 1: Write dynamic gesture tests**

Use translated open-palm fixtures and 40ms steps. Tests:

```kotlin
@Test fun slowConsistentUpwardTrajectoryTriggersSwipeUp()
@Test fun slowConsistentDownwardTrajectoryTriggersSwipeDown()
@Test fun horizontalMotionDoesNotTriggerSwipe()
@Test fun oneFrameVerticalJumpDoesNotTriggerSwipe()
@Test fun stableFistThenStableOpenTriggersOnce()
@Test fun oneFistFrameDoesNotTriggerFistThenOpen()
@Test fun fistThenOpenAfter900MsDoesNotTrigger()
@Test fun initialPinchStateDoesNotEmitEvent()
@Test fun openToClosedFor120MsTriggersPinchClose()
@Test fun closedToOpenFor120MsTriggersPinchExpand()
@Test fun thresholdJitterDoesNotTriggerPinch()
@Test fun okPoseDoesNotTriggerVolumeEvents()
```

For swipe-up, first stabilize an open palm, then translate the wrist and all landmarks from `y=0.72` to `y=0.54` over 400ms. For horizontal rejection, translate x by 0.22 while y changes by only 0.03. For the jump test, insert one frame at y=0.50 between otherwise stationary y=0.72 frames.

- [ ] **Step 2: Run dynamic tests and verify RED**

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*GestureDetectorDynamicTest' --console=plain
```

Expected: the new windowed and hysteresis cases fail or do not compile.

- [ ] **Step 3: Implement swipe trajectory analysis**

Store open-palm motion samples for 650ms. Require span at least 180ms, `abs(dy) >= 0.14`, `abs(dx) <= 0.18`, and at least 70% of non-zero vertical steps to agree with the final direction. Reject any trajectory containing a single step over 0.10 normalized units unless at least two following steps continue the same direction. Latch after firing; clear the latch when the stable pose leaves OPEN_PALM or the last 300ms displacement is below 0.035.

- [ ] **Step 4: Implement stable fist-open transition**

Record the time only when stable pose enters FIST. Emit `FistThenOpen` only when stable pose later enters OPEN_PALM within 900ms. Cancel on timeout, no-hand reset, or entry into another non-NONE stable pose. Clear after firing.

- [ ] **Step 5: Implement pinch hysteresis**

Maintain `UNKNOWN`, `OPEN`, and `CLOSED`. A ratio at or below 0.32 proposes CLOSED; a ratio at or above 0.58 proposes OPEN; the middle band preserves state. The target state must remain proposed for 120ms before committing. Initial commitment emits no event. OPEN→CLOSED emits `PinchClose`; CLOSED→OPEN emits `PinchExpand`. Suppress proposals while the per-frame or stable pose is OK_SIGN.

- [ ] **Step 6: Arbitrate events and verify GREEN**

Evaluate in order: swipe, fist-open, pinch, distinctive holds, palm hold. Apply the 650ms global cooldown only after selecting the highest-priority event; continue updating every state during cooldown. Run Step 2 and all Task 2 tests. Expected: all stability and dynamic tests pass.

---

### Task 4: MediaPipe VIDEO adapter and higher analysis resolution

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandLandmarkerWrapper.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/camera/CameraManager.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/service/CameraForegroundService.kt`

**Interfaces:**
- Changes: `HandLandmarkerWrapper.detect(imageProxy, timestampMs): List<HandPoint>?`.
- Changes: `GestureDetector.process(points: List<HandPoint>?, timestampMs: Long)`.
- Preserves: CameraManager as `ImageAnalysis.Analyzer` and all downstream events/mappings.

- [ ] **Step 1: Switch HandLandmarker options to VIDEO**

Set `RunningMode.VIDEO`, detection confidence 0.4, presence confidence 0.4, and tracking confidence 0.5. Replace `detector.detect(mpImage)` with `detector.detectForVideo(mpImage, timestampMs)`. Map the first hand's normalized landmarks to `HandPoint(it.x(), it.y(), it.z())`.

- [ ] **Step 2: Use a strict monotonic timestamp at the adapter boundary**

In CameraManager derive `rawTimestampMs = imageProxy.imageInfo.timestamp / 1_000_000L`. Keep `lastTimestampMs`; use `timestampMs = maxOf(rawTimestampMs, lastTimestampMs + 1L)` before calling both HandLandmarkerWrapper and GestureDetector. Reset this state on release.

- [ ] **Step 3: Request 1280×720 analysis**

Add `.setTargetResolution(android.util.Size(1280, 720))` to ImageAnalysis.Builder before `.build()`. Keep RGBA output and KEEP_ONLY_LATEST.

- [ ] **Step 4: Wire transition diagnostics**

Construct GestureDetector with an `onDiagnostic` callback that logs through CameraManager's tag. Keep the existing once-per-30-frame hand detection count and final GestureEvent log.

- [ ] **Step 5: Run all JVM tests, Lint, and Debug build**

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: exit code 0, zero test failures, zero lint errors, APK generated.

---

### Task 5: Device verification and one-variable calibration

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Verify: logcat tags `CameraManager`, `HandLandmarkerWrapper`, `DouyinGestureService`, and `GestureActionBridge`.

**Interfaces:**
- Produces an installed build and per-gesture expected/actual observations.

- [ ] **Step 1: Install without clearing data and launch**

```powershell
$adb='D:\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
& $adb shell am start -n com.gesturecontrol.douyin/.MainActivity
```

Expected: install `Success`, camera permission retained. Ask the user to re-enable accessibility only if the secure setting no longer contains `DouyinGestureService`.

- [ ] **Step 2: Start through the in-app button and verify VIDEO frames**

Clear logcat, click the enabled “开启手势控制” Compose button via UI bounds, collect 30 seconds. Confirm CameraX attached/active, frame count increases, no `detectForVideo` timestamp errors, and hand detection occurs when a hand is present.

- [ ] **Step 3: Test all seven default gestures in fixed order**

For each gesture, ask the user to perform ten deliberate attempts: up, down, fist-open, V hold, palm hold, pinch close, pinch expand. Record expected event, actual event, miss, and competing wrong event from logs. Point/OK/rock are tested for classification logs even if mapped to None.

- [ ] **Step 4: Apply at most one calibration variable per failing gesture**

If a gesture exceeds two wrong classifications or four misses in ten attempts, use its diagnostics to change only one of: geometry angle, normalized distance, vote ratio/duration, trajectory displacement/consistency, or pinch threshold/dwell. Add or update a failing JVM test first, verify RED, make the single change, and rerun all tests before reinstalling.

- [ ] **Step 5: Final fresh verification**

Run the full Gradle command from Task 4 Step 5 after the last calibration, reinstall, and confirm at least one successful event and accessibility completion callback for every mapped default action. Document MIUI force-stop behavior as a remaining platform constraint.
