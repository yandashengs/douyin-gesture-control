package com.gesturecontrol.douyin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.gesturecontrol.douyin.camera.CameraManager
import java.util.concurrent.Executors

/**
 * 前台服务：保持摄像头持续识别，让用户切到抖音全屏后手势控制仍然生效。
 *
 * - 继承 [LifecycleService] 以便 CameraX 的 [ProcessCameraProvider.bindToLifecycle] 能跟随生命周期
 * - 前台服务类型为 camera（Android 14+ 强制要求声明）
 * - 识别结果通过 [CameraManager] 默认回调 → [GestureActionBridge] → 无障碍服务
 *
 * 启动：ContextCompat.startForegroundService(context, Intent(context, CameraForegroundService::class.java))
 * 停止：context.stopService(Intent(context, CameraForegroundService::class.java))
 */
class CameraForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "CameraForegroundSvc"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "camera_gesture_channel"

        const val ACTION_START = "com.gesturecontrol.douyin.START"
        const val ACTION_STOP = "com.gesturecontrol.douyin.STOP"

        /** 运行状态，供 UI 查询 */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraManager: CameraManager? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        if (cameraManager == null) {
            startCamera()
        }
        return START_STICKY
    }

    private fun startCamera() {
        val manager = CameraManager(this)
        if (!manager.isReady) {
            Log.e(TAG, "HandLandmarker 未就绪，请确认 assets/hand_landmarker.task 已放入")
            manager.release()
            isRunning = false
            stopSelf()
            return
        }
        cameraManager = manager
        isRunning = false

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (cameraManager !== manager) {
                manager.release()
                return@addListener
            }
            try {
                val provider = future.get()
                cameraProvider = provider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    // 480p 足以识别手部关键点；降低分辨率可显著减少每帧
                                    // RGBA 拷贝的内存分配（720p≈3.6MB/帧 → 480p≈1.8MB/帧），
                                    // 缓解 GC 压力，提升实际帧率。
                                    android.util.Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .build()
                imageAnalysis.setAnalyzer(analysisExecutor, manager)
                Log.i(TAG, "Analyzer 已设置")

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
                isRunning = true
                Log.i(TAG, "前摄已绑定，开始识别")
            } catch (t: Throwable) {
                Log.e(TAG, "摄像头绑定失败", t)
                cameraProvider?.unbindAll()
                cameraProvider = null
                if (cameraManager === manager) {
                    cameraManager = null
                }
                manager.release()
                isRunning = false
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private var overlaySurfaceView: SurfaceView? = null

    override fun onDestroy() {
        try {
            cameraProvider?.unbindAll()
            cameraManager?.release()
            cameraProvider = null
            cameraManager = null
            analysisExecutor.shutdown()
            overlaySurfaceView?.let { sv ->
                try {
                    (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(sv)
                } catch (_: Throwable) {}
            }
            overlaySurfaceView = null
        } catch (t: Throwable) {
            Log.w(TAG, "释放资源异常", t)
        }
        isRunning = false
        super.onDestroy()
    }

    // ---------- 前台通知 ----------

    private fun startForegroundCompat() {
        ensureChannel()
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.gesturecontrol.douyin.R.string.notif_title))
            .setContentText(getString(com.gesturecontrol.douyin.R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(com.gesturecontrol.douyin.R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }
}
