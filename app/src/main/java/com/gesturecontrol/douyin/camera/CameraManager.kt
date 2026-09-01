package com.gesturecontrol.douyin.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.gesturecontrol.douyin.gesture.GestureActionMapper
import com.gesturecontrol.douyin.gesture.GestureDetector
import com.gesturecontrol.douyin.gesture.GestureEvent
import com.gesturecontrol.douyin.gesture.HandLandmarkerWrapper
import com.gesturecontrol.douyin.action.GestureActionBridge

/**
 * 摄像头识别管理器：把 CameraX 的每帧图像喂给 MediaPipe，再交给 [GestureDetector]
 * 做时序检测，最终通过 [GestureActionBridge] 派发动作。
 *
 * 作为 [ImageAnalysis.Analyzer] 实现，由 [com.gesturecontrol.douyin.service.CameraForegroundService]
 * 绑定到前摄。所有识别在 CameraX 的分析线程上执行，不会阻塞 UI。
 */
class CameraManager(
    context: Context,
    private val onEvent: (GestureEvent) -> Unit = { event ->
        // 默认行为：查用户配置映射为 DouyinAction 并派发给无障碍服务
        val mapper = GestureActionMapper.from(context)
        mapper.map(event)?.let { GestureActionBridge.dispatch(it) }
    },
) : ImageAnalysis.Analyzer {

    companion object { private const val TAG = "CameraManager" }

    private val handLandmarker = HandLandmarkerWrapper(context)
    private val gestureDetector = GestureDetector(
        onDiagnostic = { message -> Log.d(TAG, message) }
    )

    /** MediaPipe 模型是否加载成功 */
    val isReady: Boolean get() = handLandmarker.isReady

    // 诊断计数器
    @Volatile private var frameCount = 0
    @Volatile private var handDetectedCount = 0

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!handLandmarker.isReady) {
                Log.w(TAG, "HandLandmarker 未就绪，跳过帧")
                return
            }
            // 直接使用 CameraX 的原始时间戳（纳秒→毫秒）。
            // CameraX 时间戳单调递增，30fps 下相邻帧间隔约 33ms，不会出现相等。
            // 之前用 maxOf(raw, last+1) 强制让相邻帧差 1ms，会破坏 MediaPipe VIDEO
            // 模式的跨帧跟踪（跟踪算法按真实时间差预测关键点位置），导致跟踪 ID
            // 频繁重置、关键点跳变。
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L
            val landmarks = handLandmarker.detect(imageProxy, timestampMs)
            frameCount++
            if (landmarks != null) handDetectedCount++
            // 每 30 帧打一次诊断日志（约 1 秒一次）
            if (frameCount % 30 == 0) {
                Log.i(TAG, "诊断: 已处理 $frameCount 帧, 识别到手 $handDetectedCount 帧, 本帧手=${landmarks != null}")
            }
            val event = gestureDetector.process(landmarks, timestampMs)
            if (event !is GestureEvent.None) {
                Log.d(TAG, "识别到手势事件: ${event.label}")
                onEvent(event)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "analyze 异常", t)
        } finally {
            imageProxy.close()
        }
    }

    fun release() {
        handLandmarker.close()
        gestureDetector.reset()
    }
}
