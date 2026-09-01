package com.gesturecontrol.douyin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gesturecontrol.douyin.R

/**
 * 主界面：顶部标题 + 状态卡片 + 控制按钮 + 手势说明 + 最近动作。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToMapping: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部标题
            Text(
                text = stringResourceSafe(R.string.title_main),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 状态卡片
            StatusCard(state.value)

            // 主控按钮
            val ready = state.value.hasCameraPermission && state.value.isAccessibilityEnabled
            if (!state.value.isRunning) {
                Button(
                    onClick = viewModel::startControl,
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(stringResourceSafe(R.string.btn_start), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = viewModel::stopControl,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResourceSafe(R.string.btn_stop), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 快捷按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::openAccessibilitySettings,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResourceSafe(R.string.btn_open_accessibility))
                }
                OutlinedButton(
                    onClick = viewModel::openDouyin,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResourceSafe(R.string.btn_open_douyin))
                }
            }

            // 电池优化白名单：防止清后台/息屏后被系统杀进程导致无障碍断开
            if (!state.value.isIgnoringBatteryOptimizations) {
                OutlinedButton(
                    onClick = viewModel::requestIgnoreBatteryOptimizations,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResourceSafe(R.string.btn_battery_whitelist))
                }
                KeepAliveHintCard()
            }

            // 手势配置入口
            OutlinedButton(
                onClick = onNavigateToMapping,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("手势配置 · 自定义映射")
            }

            // 最近动作
            LastActionCard(state.value.lastActionLabel)

            // 手势说明
            GestureHelpCard()
        }
    }
}

@Composable
private fun StatusCard(state: MainViewModel.UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusRow("摄像头权限", state.hasCameraPermission)
            StatusRow("无障碍服务", state.isAccessibilityEnabled)
            StatusRow("电池白名单", state.isIgnoringBatteryOptimizations)
            StatusRow("手势识别", state.isRunning)
        }
    }
}

/** 保活提示：解释为什么清后台后无障碍会掉，以及 ROM 管家需要手动加白名单 */
@Composable
private fun KeepAliveHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResourceSafe(R.string.keepalive_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResourceSafe(R.string.keepalive_hint),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (ok) Color(0xFF34C759) else Color(0xFFFF3B30),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$label：${if (ok) "已就绪" else "未开启"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun LastActionCard(lastAction: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "最近动作",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                lastAction ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GestureHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResourceSafe(R.string.help_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            HelpItem(R.string.help_up)
            HelpItem(R.string.help_down)
            HelpItem(R.string.help_like)
            HelpItem(R.string.help_comment)
            HelpItem(R.string.help_vol_up)
            HelpItem(R.string.help_vol_down)
            HelpItem(R.string.help_pause)
        }
    }
}

@Composable
private fun HelpItem(textRes: Int) {
    Text(
        "•  ${stringResourceSafe(textRes)}",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

// 内部小工具：Compose 1.6+ 的 stringResource 在某些 IDE 版本自动 import 不到，统一包一层
@Composable
private fun stringResourceSafe(res: Int): String =
    androidx.compose.ui.res.stringResource(res)
