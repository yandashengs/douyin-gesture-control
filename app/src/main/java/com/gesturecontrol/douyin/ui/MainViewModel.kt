package com.gesturecontrol.douyin.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.douyin.action.GestureActionBridge
import com.gesturecontrol.douyin.service.CameraForegroundService
import com.gesturecontrol.douyin.service.DouyinGestureService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面状态与逻辑。
 * - 轮询摄像头运行状态、无障碍服务状态、最近执行的动作，更新 [uiState]
 * - 暴露启动/停止、跳转无障碍设置、打开抖音等操作
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val hasCameraPermission: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val isIgnoringBatteryOptimizations: Boolean = false,
        val isRunning: Boolean = false,
        val lastActionLabel: String? = null,
        val lastGestureLabel: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 每 500ms 刷新一次状态（轻量，无副作用）
        viewModelScope.launch {
            while (true) {
                refreshState()
                delay(500)
            }
        }
    }

    fun refreshState() {
        val ctx = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = ctx.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            isAccessibilityEnabled = isAccessibilityEnabled(ctx),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(ctx),
            isRunning = CameraForegroundService.isRunning,
            lastActionLabel = GestureActionBridge.lastAction?.label,
        )
    }

    fun startControl() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, CameraForegroundService::class.java).apply {
            action = CameraForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun stopControl() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, CameraForegroundService::class.java).apply {
            action = CameraForegroundService.ACTION_STOP
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun openAccessibilitySettings() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 打开抖音（主包名/极速版兼容） */
    fun openDouyin() {
        val ctx = getApplication<Application>()
        val pkg = listOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.ss.android.ugc.aweme.musically"
        ).firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }

        val intent = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ss.android.ugc.aweme"))
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * 请求加入电池优化白名单。清后台后无障碍服务被断开的常见根因是
     * 系统/ROM 杀进程；加入白名单后系统不再主动杀本 App。
     * 弹出的是标准系统对话框，用户点"允许"即完成。
     */
    fun requestIgnoreBatteryOptimizations() {
        val ctx = getApplication<Application>()
        if (isIgnoringBatteryOptimizations(ctx)) return
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            // 个别 ROM 不支持该 action，回退到电池优化列表页让用户手动找
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t2: Throwable) {
                // 均不支持时静默失败，UI 状态行仍会提示未加入白名单
            }
        }
    }

    /** 本 App 是否已在电池优化白名单（豁免 Doze/电池优化杀进程） */
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 检查本 App 的无障碍服务是否已开启 */
    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, DouyinGestureService::class.java)
        val expected = expectedComponent.flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
