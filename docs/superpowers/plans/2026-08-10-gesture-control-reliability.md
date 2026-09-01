# Gesture Control Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore real CameraX frame delivery on Android and make the image conversion, service state, and accessibility action pipeline reliable and observable.

**Architecture:** Keep the existing `LifecycleService -> CameraX ImageAnalysis -> MediaPipe IMAGE mode -> GestureDetector -> GestureActionBridge -> AccessibilityService` pipeline. Replace unsafe YUV/JPEG conversion with a tested packed-RGBA row copier, make camera startup lifecycle-correct and idempotent, and propagate synchronous accessibility dispatch acceptance through the bridge.

**Tech Stack:** Kotlin 1.9.24, Android SDK 34, CameraX 1.3.4, MediaPipe Tasks Vision 0.10.14, Android AccessibilityService, JUnit 4, Gradle 8.7.

## Global Constraints

- Do not migrate MediaPipe to `LIVE_STREAM`.
- Do not redesign the Compose UI or change gesture thresholds.
- Do not attempt to change MIUI system settings automatically.
- Preserve `minSdk = 26` and `targetSdk = 34` in this repair.
- Use test-first red/green cycles for new pure logic; use the already observed Android Lint `MissingSuperCall` failure as the lifecycle regression RED state.
- The workspace is not a Git repository, so commit steps are omitted.

---

### Task 1: Packed RGBA row conversion

**Files:**
- Modify: `app/build.gradle.kts:53-78`
- Create: `app/src/main/java/com/gesturecontrol/douyin/gesture/RgbaImageConverter.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/gesture/RgbaImageConverterTest.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/gesture/HandLandmarkerWrapper.kt:3-100`

**Interfaces:**
- Produces: `internal object RgbaImageConverter`
- Produces: `fun copyPackedRgba(source: ByteBuffer, width: Int, height: Int, pixelStride: Int, rowStride: Int): ByteArray`
- Produces: `fun toUprightBitmap(imageProxy: ImageProxy): Bitmap`
- Consumes: CameraX `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888`, supplied by Task 2.

- [ ] **Step 1: Write tests for tightly packed and padded rows**

Add `testImplementation("junit:junit:4.13.2")` to the app dependencies, then create:

```kotlin
class RgbaImageConverterTest {
    @Test fun copiesTightlyPackedRows() {
        val source = ByteBuffer.wrap(byteArrayOf(1,2,3,4, 5,6,7,8))
        assertArrayEquals(
            byteArrayOf(1,2,3,4, 5,6,7,8),
            RgbaImageConverter.copyPackedRgba(source, 2, 1, 4, 8),
        )
    }

    @Test fun removesRowPadding() {
        val source = ByteBuffer.wrap(byteArrayOf(
            1,2,3,4, 9,9,9,9,
            5,6,7,8, 9,9,9,9,
        ))
        assertArrayEquals(
            byteArrayOf(1,2,3,4, 5,6,7,8),
            RgbaImageConverter.copyPackedRgba(source, 1, 2, 4, 8),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedPixelStride() {
        RgbaImageConverter.copyPackedRgba(ByteBuffer.allocate(4), 1, 1, 2, 4)
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*RgbaImageConverterTest' --console=plain
```

Expected: compilation fails because `RgbaImageConverter` does not exist.

- [ ] **Step 3: Implement packed row copying and Bitmap rotation**

`copyPackedRgba` must validate positive dimensions, require `pixelStride == 4`, require `rowStride >= width * 4`, duplicate and rewind the source buffer, verify the final requested byte is within `limit`, then copy exactly `width * 4` bytes per row into a compact output array. `toUprightBitmap` must require one RGBA plane, call the row copier, populate an `ARGB_8888` Bitmap with `copyPixelsFromBuffer`, rotate by `imageInfo.rotationDegrees`, and recycle the unrotated Bitmap only when a distinct rotated Bitmap is created.

- [ ] **Step 4: Replace HandLandmarkerWrapper YUV/JPEG conversion**

Remove `BitmapFactory`, `ImageFormat`, `Rect`, `YuvImage`, `Image`, and `ByteArrayOutputStream`. In `detect`, call:

```kotlin
val bitmap = RgbaImageConverter.toUprightBitmap(imageProxy)
val mpImage = BitmapImageBuilder(bitmap).build()
```

Retain exception logging and synchronous MediaPipe detection.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all three tests pass.

---

### Task 2: Lifecycle-correct and truthful camera service

**Files:**
- Modify: `app/src/main/java/com/gesturecontrol/douyin/service/CameraForegroundService.kt:51-125`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/camera/CameraManager.kt:35-62`

**Interfaces:**
- Consumes: `RgbaImageConverter.toUprightBitmap(ImageProxy)` through HandLandmarkerWrapper.
- Preserves: `CameraForegroundService.isRunning: Boolean` for MainViewModel.
- Produces: ImageAnalysis frames in RGBA_8888 format.

- [ ] **Step 1: Reconfirm lifecycle RED evidence**

Use the saved lint report and source line showing `MissingSuperCall` at `CameraForegroundService.kt:60`. No production edit occurs before this check.

- [ ] **Step 2: Call LifecycleService parent and make START idempotent**

At the start of `onStartCommand`, invoke:

```kotlin
super.onStartCommand(intent, flags, startId)
```

Handle STOP after that call. For START/default, call `startForegroundCompat()` and invoke `startCamera()` only when `cameraManager == null`; do not set `isRunning` here.

- [ ] **Step 3: Configure RGBA and update state after real binding**

Add to ImageAnalysis.Builder:

```kotlin
.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
```

After successful `bindToLifecycle`, set `isRunning = true`. If the model is unavailable or binding throws, release the manager, clear `cameraManager`, set `isRunning = false`, and stop the service. Remove the per-frame debug log from the Analyzer wrapper; retain CameraManager's once-per-30-frame diagnostic.

- [ ] **Step 4: Make teardown consistently clear state**

On destroy, unbind CameraX, release the manager, clear provider/manager references, shut down the executor, and set `isRunning=false`. Keep the existing guarded resource cleanup.

- [ ] **Step 5: Run Android Lint for the lifecycle check**

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:lintDebug --console=plain
```

Expected: no `MissingSuperCall` and no `UnsafeOptInUsageError`; warnings may remain for unrelated target SDK, orientation, dependency age, icon, and unused resources.

---

### Task 3: Honest accessibility action results

**Files:**
- Create: `app/src/main/java/com/gesturecontrol/douyin/action/AcceptedActionTracker.kt`
- Create: `app/src/test/java/com/gesturecontrol/douyin/action/AcceptedActionTrackerTest.kt`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/action/GestureActionBridge.kt:16-61`
- Modify: `app/src/main/java/com/gesturecontrol/douyin/service/DouyinGestureService.kt:71-162`

**Interfaces:**
- Changes: `ActionExecutor.execute(action: DouyinAction): Boolean`.
- Produces: `AcceptedActionTracker.record(action: DouyinAction, accepted: Boolean): Boolean`.
- Preserves: `GestureActionBridge.dispatch(action): Boolean`, `lastAction`, and `lastActionTime` consumer API.

- [ ] **Step 1: Write tracker tests and verify RED**

```kotlin
class AcceptedActionTrackerTest {
    @Test fun acceptedActionUpdatesState() {
        val tracker = AcceptedActionTracker { 123L }
        assertTrue(tracker.record(DouyinAction.SwipeUp, true))
        assertSame(DouyinAction.SwipeUp, tracker.lastAction)
        assertEquals(123L, tracker.lastActionTime)
    }

    @Test fun rejectedActionDoesNotReplaceState() {
        val tracker = AcceptedActionTracker { 1L }
        tracker.record(DouyinAction.SwipeUp, true)
        assertFalse(tracker.record(DouyinAction.SwipeDown, false))
        assertSame(DouyinAction.SwipeUp, tracker.lastAction)
        assertEquals(1L, tracker.lastActionTime)
    }
}
```

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests '*AcceptedActionTrackerTest' --console=plain
```

Expected: compilation fails because `AcceptedActionTracker` does not exist.

- [ ] **Step 2: Implement the tracker and verify GREEN**

Implement a clock-injected internal class with volatile read-only state. `record` returns false without mutation when rejected; when accepted it stores action and clock time and returns true. Re-run the focused test; expect both tests to pass.

- [ ] **Step 3: Propagate Boolean acceptance through the bridge**

Make `ActionExecutor.execute` return Boolean. In `GestureActionBridge.dispatch`, call `target.execute(action)`, pass its result to the tracker, log rejected actions, and return the tracker result. Preserve exception handling.

- [ ] **Step 4: Return dispatchGesture acceptance and add callbacks**

Make tap/swipe/double-tap helpers return Boolean. Centralize dispatch in:

```kotlin
private fun dispatch(action: DouyinAction, gesture: GestureDescription): Boolean =
    dispatchGesture(gesture, object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            Log.i(TAG, "手势执行完成: ${action.label}")
        }
        override fun onCancelled(gestureDescription: GestureDescription) {
            Log.w(TAG, "手势执行取消: ${action.label}")
        }
    }, null)
```

Return true after volume adjustment, false for `None`, and use the relevant action in each helper.

- [ ] **Step 5: Correct double-tap stroke semantics**

Construct both tap strokes without `willContinue=true`; retain their start times and 50 ms durations so the first pointer is lifted before the second tap.

- [ ] **Step 6: Run all JVM tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: all RGBA converter and action tracker tests pass.

---

### Task 4: Build and Android device verification

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Verify: `app/build/reports/lint-results-debug.html`

**Interfaces:**
- Consumes the completed camera and action pipeline.
- Produces fresh build, lint, and device-log evidence.

- [ ] **Step 1: Run the full local verification suite**

```powershell
$env:JAVA_HOME='D:\Program\AndroidStudio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: exit code 0, zero test failures, zero lint errors, APK generated.

- [ ] **Step 2: Install the APK without clearing app data**

```powershell
& 'D:\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r 'app\build\outputs\apk\debug\app-debug.apk'
```

Expected: `Success`. Re-check `enabled_accessibility_services`; if MIUI disabled it, ask the user to re-enable it manually before action verification.

- [ ] **Step 3: Launch, start the foreground service, and collect bounded logs**

Launch the MainActivity, start the service through the app UI or an explicit foreground-service command while the app is foreground, clear logcat immediately beforehand, then collect the relevant tags for up to 30 seconds. Do not automate changes to secure accessibility settings.

Expected logs include:

```text
Analyzer 已设置
前摄已绑定，开始识别
诊断: 已处理 30 帧
```

CameraX diagnostics must show the ImageAnalysis use case attached and active, not `Active and attached use case: []`.

- [ ] **Step 4: Verify hand and action behavior**

Ask the user to place a hand in view and perform one configured gesture. Confirm `handDetectedCount` increases, a GestureEvent is logged, GestureActionBridge reports acceptance, and AccessibilityService reports completion. If accessibility is disabled, report that exact external blocker without changing the setting programmatically.

- [ ] **Step 5: Review final diff and report residual platform constraints**

Review all changed files and state that MIUI recent-task swipe force-stop remains a platform behavior. Recommend background unrestricted/autostart/recents lock only as user-operated settings, not application code.
