package com.gesturecontrol.douyin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gesturecontrol.douyin.ui.GestureMappingScreen
import com.gesturecontrol.douyin.ui.MainScreen

/**
 * 入口 Activity。
 * - 首次启动请求摄像头权限
 * - 屏幕常亮（洗澡时手机挂墙上不锁屏）
 * - 加载 Compose 主界面 + 导航到手势配置页
 */
class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果由 ViewModel 轮询反映到 UI，无需在此处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 请求摄像头权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme(
                colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(this)
                } else {
                    lightColorScheme()
                }
            ) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onNavigateToMapping = { navController.navigate("mapping") }
                        )
                    }
                    composable("mapping") {
                        GestureMappingScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    // 用户从无障碍设置返回时，让 ViewModel 立即刷新状态
    override fun onResume() {
        super.onResume()
        // ViewModel 的轮询会自动捕获，这里无需额外操作
    }
}
