package com.gesturecontrol.douyin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gesturecontrol.douyin.action.DouyinAction
import com.gesturecontrol.douyin.gesture.GestureActionMapper
import com.gesturecontrol.douyin.gesture.PresetGesture

/**
 * 手势映射配置页面。
 *
 * 功能：
 * - 列出所有 [PresetGesture]，每项显示代码绘制的示意图 + 名称 + 说明
 * - 每项有下拉框选对应 [DouyinAction]
 * - 改完自动保存到 [MappingRepository]，立即生效
 * - 顶部有「重置默认」按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureMappingScreen(
    onBack: () -> Unit,
    viewModel: MappingViewModel = viewModel(),
) {
    val mapping by viewModel.mapping.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手势配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefault() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置默认")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            items(PresetGesture.entries) { gesture ->
                GestureMappingCard(
                    gesture = gesture,
                    currentAction = mapping[gesture] ?: DouyinAction.None,
                    onActionChange = { viewModel.set(gesture, it) },
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun GestureMappingCard(
    gesture: PresetGesture,
    currentAction: DouyinAction,
    onActionChange: (DouyinAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：示意图
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    gesture.drawIcon.invoke(this)
                }
            }

            Spacer(Modifier.width(16.dp))

            // 中间：名称 + 说明
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gesture.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = gesture.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            // 右侧：动作下拉
            ActionDropdown(currentAction = currentAction, onActionChange = onActionChange)
        }
    }
}

@Composable
private fun ActionDropdown(
    currentAction: DouyinAction,
    onActionChange: (DouyinAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = currentAction.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (currentAction is DouyinAction.None) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // 用 for 循环 + 显式非空断言，避免 Compose 重组时 lambda 捕获 null
            for (action in DouyinAction.all) {
                if (action == null) continue
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onActionChange(action)
                        expanded = false
                    },
                )
            }
        }
    }
}
