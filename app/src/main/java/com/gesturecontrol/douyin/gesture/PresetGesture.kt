package com.gesturecontrol.douyin.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 预设手势库 - 用户可在 UI 里把这些手势映射到任意 [com.gesturecontrol.douyin.action.DouyinAction]。
 *
 * 每个 [PresetGesture] 对应：
 *  - 一个 [GestureEvent]（由 [GestureDetector] 产出）
 *  - 一个示意图绘制函数 [drawIcon]（在 Compose Canvas 里用代码画）
 *  - 一段用户可读的说明 [description]
 *
 * 新增手势只需在此枚举里添加一项，并在 [GestureDetector] 里实现识别逻辑。
 */
enum class PresetGesture(
    val id: String,
    val displayName: String,
    val description: String,
    val event: GestureEvent,
    val drawIcon: DrawScope.() -> Unit,
) {
    SWIPE_UP(
        id = "swipe_up",
        displayName = "上挥",
        description = "张开手掌向上挥动 → 下一个视频",
        event = GestureEvent.SwipeUp,
        drawIcon = { drawSwipeIcon(up = true) },
    ),

    SWIPE_DOWN(
        id = "swipe_down",
        displayName = "下挥",
        description = "张开手掌向下挥动 → 上一个视频",
        event = GestureEvent.SwipeDown,
        drawIcon = { drawSwipeIcon(up = false) },
    ),

    FIST_THEN_OPEN(
        id = "fist_then_open",
        displayName = "握拳张开",
        description = "握拳后快速张开 → 双击点赞",
        event = GestureEvent.FistThenOpen,
        drawIcon = { drawFistThenOpenIcon() },
    ),

    V_SIGN_HOLD(
        id = "v_sign_hold",
        displayName = "V字保持",
        description = "食指+中指伸出保持 → 评论区",
        event = GestureEvent.VSignHold,
        drawIcon = { drawVSignIcon() },
    ),

    PALM_HOLD(
        id = "palm_hold",
        displayName = "手掌保持",
        description = "张开手掌保持不动 → 暂停/播放",
        event = GestureEvent.PalmHold,
        drawIcon = { drawPalmHoldIcon() },
    ),

    PINCH_CLOSE(
        id = "pinch_close",
        displayName = "捏合",
        description = "拇指食指捏合 → 音量-",
        event = GestureEvent.PinchClose,
        drawIcon = { drawPinchIcon(expand = false) },
    ),

    PINCH_EXPAND(
        id = "pinch_expand",
        displayName = "张开",
        description = "拇指食指张开 → 音量+",
        event = GestureEvent.PinchExpand,
        drawIcon = { drawPinchIcon(expand = true) },
    ),

    POINT_HOLD(
        id = "point_hold",
        displayName = "指向保持",
        description = "仅食指伸出保持 → 自定义动作",
        event = GestureEvent.PointHold,
        drawIcon = { drawPointIcon() },
    ),

    OK_SIGN(
        id = "ok_sign",
        displayName = "OK手势",
        description = "拇指食指成圈，其余伸直 → 自定义动作",
        event = GestureEvent.OkSign,
        drawIcon = { drawOkIcon() },
    ),

    ROCK_SIGN(
        id = "rock_sign",
        displayName = "摇滚手势",
        description = "食指+小指伸出 → 自定义动作",
        event = GestureEvent.RockSign,
        drawIcon = { drawRockIcon() },
    );

    companion object {
        fun fromId(id: String): PresetGesture? = entries.firstOrNull { it.id == id }

        /** 默认映射：首次启动时用这套配置 */
        val defaultMapping: Map<PresetGesture, com.gesturecontrol.douyin.action.DouyinAction> = mapOf(
            SWIPE_UP to com.gesturecontrol.douyin.action.DouyinAction.SwipeUp,
            SWIPE_DOWN to com.gesturecontrol.douyin.action.DouyinAction.SwipeDown,
            FIST_THEN_OPEN to com.gesturecontrol.douyin.action.DouyinAction.DoubleTap,
            V_SIGN_HOLD to com.gesturecontrol.douyin.action.DouyinAction.ToggleComments,
            PALM_HOLD to com.gesturecontrol.douyin.action.DouyinAction.TogglePause,
            PINCH_CLOSE to com.gesturecontrol.douyin.action.DouyinAction.VolumeDown,
            PINCH_EXPAND to com.gesturecontrol.douyin.action.DouyinAction.VolumeUp,
            POINT_HOLD to com.gesturecontrol.douyin.action.DouyinAction.CloseComments,
            OK_SIGN to com.gesturecontrol.douyin.action.DouyinAction.None,
            ROCK_SIGN to com.gesturecontrol.douyin.action.DouyinAction.None,
        )
    }
}

// ============================================================
// 示意图绘制函数 - 全部用 Canvas 代码画，无需图片资源
// ============================================================

private val HAND_COLOR = Color(0xFF4A90E2)
private val ARROW_COLOR = Color(0xFFFF9500)
private val STROKE_WIDTH = 4.dp

/** 通用：画一个张开的手掌轮廓 */
private fun DrawScope.drawPalm(centerX: Float, centerY: Float, scale: Float, fingersUp: Int) {
    val w = size.width
    val s = scale * w * 0.4f

    val stroke = Stroke(width = STROKE_WIDTH.toPx())
    val handPath = Path().apply {
        // 手掌
        moveTo(centerX - s * 0.3f, centerY + s * 0.3f)
        lineTo(centerX - s * 0.3f, centerY - s * 0.1f)
        // 5 根手指
        val fingerList = listOf(-0.3f, -0.15f, 0f, 0.15f, 0.3f)
        fingerList.forEachIndexed { idx, xOffset ->
            val fingerUp = (fingersUp shr idx) and 1 == 1
            val tipY = if (fingerUp) centerY - s * 0.5f else centerY - s * 0.15f
            lineTo(centerX + xOffset * s, centerY - s * 0.1f)
            lineTo(centerX + xOffset * s, tipY)
            lineTo(centerX + xOffset * s + s * 0.05f, tipY)
        }
        lineTo(centerX + s * 0.3f, centerY + s * 0.3f)
        close()
    }
    drawPath(handPath, color = HAND_COLOR, style = stroke)
}

/** 上挥/下挥 */
private fun DrawScope.drawSwipeIcon(up: Boolean) {
    val cx = size.width / 2
    val cy = size.height / 2
    drawPalm(cx, cy, 0.6f, fingersUp = 0b11111)

    val arrowPath = Path().apply {
        val arrowY = if (up) cy + size.height * 0.3f else cy - size.height * 0.3f
        val arrowTipY = if (up) cy - size.height * 0.35f else cy + size.height * 0.35f
        moveTo(cx, arrowTipY)
        lineTo(cx - 20f, arrowTipY + (if (up) 30f else -30f))
        moveTo(cx, arrowTipY)
        lineTo(cx + 20f, arrowTipY + (if (up) 30f else -30f))
        moveTo(cx, arrowTipY)
        lineTo(cx, arrowY)
    }
    drawPath(arrowPath, color = ARROW_COLOR, style = Stroke(width = STROKE_WIDTH.toPx()))
}

/** 握拳→张开 */
private fun DrawScope.drawFistThenOpenIcon() {
    val w = size.width
    drawPalm(w * 0.3f, size.height / 2, 0.4f, fingersUp = 0b00000)
    drawPalm(w * 0.7f, size.height / 2, 0.4f, fingersUp = 0b11111)
    val arrow = Path().apply {
        moveTo(w * 0.45f, size.height / 2)
        lineTo(w * 0.55f, size.height / 2)
        lineTo(w * 0.52f, size.height / 2 - 10f)
        moveTo(w * 0.55f, size.height / 2)
        lineTo(w * 0.52f, size.height / 2 + 10f)
    }
    drawPath(arrow, color = ARROW_COLOR, style = Stroke(width = STROKE_WIDTH.toPx()))
}

/** V 字 */
private fun DrawScope.drawVSignIcon() {
    val cx = size.width / 2
    val cy = size.height / 2
    val stroke = Stroke(width = STROKE_WIDTH.toPx())
    val path = Path().apply {
        moveTo(cx - 30f, cy + 40f)
        lineTo(cx + 30f, cy + 40f)
        lineTo(cx + 30f, cy)
        lineTo(cx - 30f, cy)
        close()
        moveTo(cx - 10f, cy); lineTo(cx - 20f, cy - 60f)
        moveTo(cx + 10f, cy); lineTo(cx + 20f, cy - 60f)
    }
    drawPath(path, color = HAND_COLOR, style = stroke)
}

/** 张开掌保持 */
private fun DrawScope.drawPalmHoldIcon() {
    val cx = size.width / 2
    val cy = size.height / 2
    drawPalm(cx, cy, 0.55f, fingersUp = 0b11111)
    val clockPath = Path().apply {
        addArc(Rect(center = Offset(cx, cy), radius = 80f), 0f, 360f)
        moveTo(cx, cy); lineTo(cx, cy - 50f)
    }
    drawPath(clockPath, color = ARROW_COLOR, style = Stroke(width = 2.dp.toPx()))
}

/** 捏合/张开 */
private fun DrawScope.drawPinchIcon(expand: Boolean) {
    val cx = size.width / 2
    val cy = size.height / 2
    val stroke = Stroke(width = STROKE_WIDTH.toPx())

    val thumbPath = Path().apply {
        val tipX = if (expand) cx - 40f else cx - 10f
        moveTo(cx, cy + 20f); lineTo(tipX, cy - 10f)
    }
    val indexPath = Path().apply {
        val tipX = if (expand) cx + 40f else cx + 10f
        moveTo(cx, cy + 20f); lineTo(tipX, cy - 10f)
    }
    drawPath(thumbPath, color = HAND_COLOR, style = stroke)
    drawPath(indexPath, color = HAND_COLOR, style = stroke)

    if (expand) {
        val arrow = Path().apply {
            moveTo(cx - 50f, cy + 40f); lineTo(cx - 70f, cy + 30f)
            moveTo(cx - 50f, cy + 40f); lineTo(cx - 70f, cy + 50f)
            moveTo(cx + 50f, cy + 40f); lineTo(cx + 70f, cy + 30f)
            moveTo(cx + 50f, cy + 40f); lineTo(cx + 70f, cy + 50f)
        }
        drawPath(arrow, color = ARROW_COLOR, style = Stroke(width = 2.dp.toPx()))
    }
}

/** 食指指向 */
private fun DrawScope.drawPointIcon() {
    val cx = size.width / 2
    val cy = size.height / 2
    val stroke = Stroke(width = STROKE_WIDTH.toPx())
    val path = Path().apply {
        moveTo(cx - 25f, cy + 40f)
        lineTo(cx + 25f, cy + 40f)
        lineTo(cx + 25f, cy)
        lineTo(cx - 25f, cy)
        close()
        moveTo(cx, cy); lineTo(cx, cy - 70f)
    }
    drawPath(path, color = HAND_COLOR, style = stroke)
}

/** OK 手势 */
private fun DrawScope.drawOkIcon() {
    val cx = size.width / 2
    val cy = size.height / 2
    val stroke = Stroke(width = STROKE_WIDTH.toPx())
    val path = Path().apply {
        addArc(Rect(center = Offset(cx - 25f, cy + 10f), radius = 25f), 0f, 360f)
        moveTo(cx, cy - 10f); lineTo(cx + 10f, cy - 60f)
        moveTo(cx + 15f, cy - 10f); lineTo(cx + 25f, cy - 60f)
        moveTo(cx + 30f, cy - 10f); lineTo(cx + 40f, cy - 50f)
    }
    drawPath(path, color = HAND_COLOR, style = stroke)
}

/** 摇滚手势 */
private fun DrawScope.drawRockIcon() {
    val cx = size.width / 2
    val cy = size.height / 2
    val stroke = Stroke(width = STROKE_WIDTH.toPx())
    val path = Path().apply {
        moveTo(cx - 30f, cy + 40f)
        lineTo(cx + 30f, cy + 40f)
        lineTo(cx + 30f, cy)
        lineTo(cx - 30f, cy)
        close()
        moveTo(cx - 15f, cy); lineTo(cx - 20f, cy - 60f)
        moveTo(cx + 15f, cy); lineTo(cx + 20f, cy - 60f)
    }
    drawPath(path, color = HAND_COLOR, style = stroke)
}
