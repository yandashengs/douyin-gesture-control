package com.gesturecontrol.douyin.gesture

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * MediaPipe HandLandmarker 封装。
 *
 * - 模型文件 [MODEL_PATH] 放在 app/src/main/assets/，需用户自行下载（见 README）
 * - 用 [RunningMode.VIDEO] 同步模式，利用跨帧跟踪稳定远距离关键点
 * - CameraX 输出 RGBA_8888，本类把带行填充的 plane 紧凑复制并旋转为正向 Bitmap
 *
 * 检测结果返回每只手的 21 个归一化关键点（坐标范围 0~1，相对图像宽高）。
 * 关键点索引：0=手腕, 4=拇指尖, 8=食指尖, 12=中指尖, 16=无名指尖, 20=小指尖
 */
class HandLandmarkerWrapper(context: Context) {

    companion object {
        private const val TAG = "HandLandmarkerWrapper"
        const val MODEL_PATH = "hand_landmarker.task"
    }

    private val landmarker: HandLandmarker? = try {
        val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_PATH).build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            // 提高置信度阈值：原 0.4 过低，弱光/远距离/侧手时反复触发
            // "检测到 → 丢失 → 重新检测"，跟踪 ID 频繁重置导致关键点跳变。
            // 0.5/0.5/0.6 让 MediaPipe 更保守地锁定已跟踪的手，减少抖动。
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.6f)
            .build()
        HandLandmarker.createFromOptions(context, options)
    } catch (t: Throwable) {
        Log.e(TAG, "初始化 HandLandmarker 失败，请确认 assets/$MODEL_PATH 已放入", t)
        null
    }

    val isReady: Boolean get() = landmarker != null

    /**
     * 对一帧图像进行手部关键点检测。
     * @return 第一只手的 21 个关键点；无手时返回 null
     */
    fun detect(imageProxy: ImageProxy, timestampMs: Long): List<HandPoint>? {
        val detector = landmarker ?: return null
        return try {
            val bitmap = RgbaImageConverter.toUprightBitmap(imageProxy)
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result: HandLandmarkerResult = detector.detectForVideo(mpImage, timestampMs)
                result.landmarks().firstOrNull()?.map {
                    HandPoint(it.x(), it.y(), it.z())
                }
            } finally {
                bitmap.recycle()
            }
        } catch (t: Throwable) {
            val planeInfo = imageProxy.planes.firstOrNull()?.let {
                "pixelStride=${it.pixelStride}, rowStride=${it.rowStride}"
            } ?: "无图像 plane"
            Log.w(
                TAG,
                "detectForVideo 失败: ${imageProxy.width}x${imageProxy.height}, " +
                    "rotation=${imageProxy.imageInfo.rotationDegrees}, timestamp=$timestampMs, $planeInfo",
                t,
            )
            null
        }
    }

    fun close() {
        landmarker?.close()
    }
}
