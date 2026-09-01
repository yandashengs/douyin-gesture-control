# 手势控制完整修复设计

## 目标

修复 Android 实机上手势无响应的问题，并加固摄像头取帧、MediaPipe 图像输入、服务运行状态和无障碍动作派发链路。成功标准如下：

- CameraX 的 `ImageAnalysis` 在前台服务启动后持续收到帧。
- MediaPipe 能获得方向正确、像素布局可靠的 Bitmap，并持续输出手部关键点或明确的“未检测到手”诊断。
- UI 仅在摄像头用例真正绑定后显示识别运行中。
- 无障碍动作只有在系统接受派发后才记为最近动作；派发取消会留下日志。
- 双击动作由两个独立点击组成，不留下未结束的连续触摸。
- 单元测试、Android Lint 和 Debug 构建通过；在已连接手机上能从日志观察到连续分析帧。

## 已确认根因

`CameraForegroundService` 继承 `LifecycleService`，但其 `onStartCommand()` 没有调用父类实现。因此服务生命周期没有进入 STARTED，CameraX 日志显示 ImageAnalysis 用例未附着，Analyzer 从未收到图像。Android Lint 同时报告 `MissingSuperCall`。

手机日志还显示，用户从最近任务划掉应用后，MIUI 以 `SwipeUpClean` 强制停止了进程；这种系统行为会终止前台服务并关闭无障碍服务，不能由普通应用绕过。

## 方案选择

采用针对当前架构的加固方案，不切换到 MediaPipe `LIVE_STREAM`，也不重写 UI：

1. 保留 `LifecycleService + ImageAnalysis.Analyzer + RunningMode.IMAGE` 数据流。
2. CameraX 输出改为 `RGBA_8888`，替代当前忽略 YUV 行步长/像素步长的 NV21 拼接和每帧 JPEG 编解码。
3. 服务只初始化一次相机，并以 CameraX 真实绑定结果维护运行状态。
4. 动作执行接口返回同步“是否被系统接受”，异步回调只负责记录完成/取消。

## 组件改动

### CameraForegroundService

- `onStartCommand()` 调用 `super.onStartCommand(intent, flags, startId)`，确保 `LifecycleService` 分发生命周期事件。
- 对重复的 START intent 做幂等保护，避免创建多个 CameraManager 和重复绑定。
- ImageAnalysis 指定 `OUTPUT_IMAGE_FORMAT_RGBA_8888`。
- `isRunning` 仅在 `bindToLifecycle()` 成功后设为 true；启动、失败、停止和销毁分别维护明确状态。
- STOP 路径仍让父类先接收启动事件，再停止服务，保持生命周期契约完整。

### HandLandmarkerWrapper

- 输入转换改为从 RGBA plane 读取像素。
- 正确处理 plane 的 `rowStride`，逐行复制有效像素，不能假定每行紧密排列。
- 根据 CameraX rotationDegrees 旋转 Bitmap。
- 保持同步 MediaPipe 检测接口不变，使 GestureDetector 和映射层无需重写。
- 转换失败时记录图像尺寸、行步长和旋转角度，便于实机诊断。

### 无障碍动作链路

- `ActionExecutor.execute()` 返回 Boolean。
- `dispatchGesture()` 的同步返回值表示系统是否接受请求；GestureActionBridge 只在 true 时更新最近动作。
- 为模拟手势增加 `GestureResultCallback`，记录 completed/cancelled。
- 双击的第一个 stroke 不再声明 `willContinue=true`，两次点击均为独立按下/抬起。
- 音量调整成功调用后返回 true；None 返回 false。

### UI 状态

- 保持现有轮询结构，减少改动范围。
- `isRunning` 改为反映相机真实绑定状态，避免“服务已启动但没有帧”的假阳性。
- 本次不增加摄像头预览；帧计数和错误继续通过日志验证。

## 错误处理

- 模型初始化失败：不绑定 Analyzer 为“运行中”，输出模型加载异常。
- 摄像头绑定失败：释放本次 CameraManager，保持 `isRunning=false`。
- 图像 plane 格式异常：跳过该帧并记录诊断，不让分析线程崩溃。
- 手势派发被拒绝或取消：不更新最近动作，并记录动作类型。
- MIUI 强制停止：应用无法自行恢复；测试与使用说明要求开启无障碍、后台无限制并避免从最近任务划掉。

## 测试与验证

1. 以现有 Lint 的 `MissingSuperCall` 失败作为生命周期修复的 RED 证据。
2. 为可提取的 RGBA 行复制逻辑添加 JVM 单元测试，覆盖紧密行、带 padding 行和非法步长。
3. 为动作桥接的成功/拒绝状态添加 JVM 单元测试时，避免依赖 Android AccessibilityService；若现有单例结构使测试必须大量 mock，则只做最小纯逻辑提取。
4. 运行 `testDebugUnitTest`、`lintDebug` 和 `assembleDebug`。
5. 安装 Debug APK 到当前 ADB 设备，重新打开无障碍后启动识别，验证：
   - 服务与无障碍桥接成功；
   - CameraX 用例处于 attached/active；
   - `analyze` 帧计数持续增长；
   - 展示手掌时 handDetectedCount 增长；
   - 触发手势时动作被系统接受并出现完成回调。

## 非目标

- 不自动修改 MIUI 的自启动、省电或无障碍系统设置。
- 不重做手势分类算法与阈值。
- 不迁移到 MediaPipe LIVE_STREAM。
- 不重新设计 Compose 界面。
