package com.gesturecontrol.douyin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gesturecontrol.douyin.action.ActionExecutor
import com.gesturecontrol.douyin.action.DouyinAction
import com.gesturecontrol.douyin.action.GestureActionBridge

/**
 * 无障碍服务：跨 App 模拟手势控制抖音。
 *
 * - 滑动用 [GestureDescription] + [dispatchGesture]
 * - 音量直接调 [AudioManager] 的 STREAM_MUSIC
 * - 通过 [GestureActionBridge] 单例接收来自摄像头识别端的指令
 *
 * 需要用户在「设置 → 无障碍」里手动开启本服务。
 */
class DouyinGestureService : AccessibilityService(), ActionExecutor {

    companion object {
        private const val TAG = "DouyinGestureService"

        // 抖音 UI 元素的大致相对位置（按屏幕比例），不同机型可能需要微调
        private const val TAP_CENTER_X_RATIO = 0.5f        // 双击点赞 / 暂停
        private const val TAP_CENTER_Y_RATIO = 0.5f
        private const val COMMENT_X_RATIO = 0.92f          // 右侧评论按钮
        private const val COMMENT_Y_RATIO = 0.58f

        // 滑动参数
        private const val SWIPE_DURATION_MS = 350L
        private const val SWIPE_START_RATIO = 0.75f        // 从屏幕 75% 高度开始
        private const val SWIPE_END_RATIO = 0.25f          // 滑到 25% 高度
        private const val SWIPE_X_RATIO = 0.5f

        // 双击点赞参数
        private const val TAP_DURATION_MS = 50L
        private const val DOUBLE_TAP_GAP_MS = 80L
    }

    private lateinit var audioManager: AudioManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 生命周期回调内绝不能抛异常：无障碍服务崩溃后系统会直接禁用它，
        // 用户就必须去设置里手动重新开启（清后台后"无障碍被关"的主要根因之一）。
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            GestureActionBridge.bind(this)
            Log.i(TAG, "无障碍服务已连接，已绑定到 GestureActionBridge")
        } catch (t: Throwable) {
            Log.e(TAG, "onServiceConnected 初始化失败", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件，只用 dispatchGesture 主动模拟手势
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        try {
            GestureActionBridge.unbind()
        } catch (t: Throwable) {
            Log.e(TAG, "onDestroy 解绑失败", t)
        } finally {
            super.onDestroy()
        }
        Log.i(TAG, "无障碍服务销毁，已解绑")
    }

    // ---------- ActionExecutor 实现 ----------

    override fun execute(action: DouyinAction): Boolean =
        when (action) {
            DouyinAction.SwipeUp -> swipeVertical(up = true)
            DouyinAction.SwipeDown -> swipeVertical(up = false)
            DouyinAction.DoubleTap -> doubleTapCenter()
            DouyinAction.TogglePause -> tapCenter()
            DouyinAction.ToggleComments -> tapCommentButton()
            DouyinAction.CloseComments -> closeComments()
            DouyinAction.VolumeUp -> adjustVolume(up = true)
            DouyinAction.VolumeDown -> adjustVolume(up = false)
            DouyinAction.None -> false
        }

    // ---------- 具体手势实现 ----------

    /** 上滑（下一个视频）/ 下滑（上一个视频） */
    private fun swipeVertical(up: Boolean): Boolean {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val x = w * SWIPE_X_RATIO
        val startY = if (up) h * SWIPE_START_RATIO else h * SWIPE_END_RATIO
        val endY = if (up) h * SWIPE_END_RATIO else h * SWIPE_START_RATIO

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val action = if (up) DouyinAction.SwipeUp else DouyinAction.SwipeDown
        return dispatchSyntheticGesture(action, gesture)
    }

    /** 单击屏幕中央（暂停/播放） */
    private fun tapCenter(): Boolean =
        tap(DouyinAction.TogglePause, TAP_CENTER_X_RATIO, TAP_CENTER_Y_RATIO)

    /** 双击点赞：优先用节点查找精准定位，失败则回退到屏幕中央双击 */
    private fun doubleTapCenter(): Boolean {
        // 优先通过无障碍节点查找点赞按钮。
        // 抖音点赞按钮的 contentDescription 通常包含"点赞"（未点赞）或"已点赞"（已点赞）。
        // 找到后用其屏幕坐标做双击，避免硬编码坐标在不同机型/抖音版本上偏移。
        val likeNode = findNodeByDescription(rootInActiveWindow, "点赞")
        if (likeNode != null) {
            val rect = Rect()
            likeNode.getBoundsInScreen(rect)
            val x = rect.exactCenterX()
            val y = rect.exactCenterY()
            // 双重保险：检查坐标有效性，无效则回退到屏幕中央双击
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            if (x in 0f..w && y in 0f..h) {
                Log.i(TAG, "点赞节点定位成功: x=$x, y=$y, desc=${likeNode.contentDescription}")
                val path1 = Path().apply { moveTo(x, y) }
                val stroke1 = GestureDescription.StrokeDescription(path1, 0L, TAP_DURATION_MS)
                val path2 = Path().apply { moveTo(x, y) }
                val stroke2 = GestureDescription.StrokeDescription(
                    path2,
                    TAP_DURATION_MS + DOUBLE_TAP_GAP_MS,
                    TAP_DURATION_MS
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke1)
                    .addStroke(stroke2)
                    .build()
                return dispatchSyntheticGesture(DouyinAction.DoubleTap, gesture)
            } else {
                Log.w(TAG, "点赞节点坐标无效: x=$x, y=$y，回退到屏幕中央双击")
            }
        } else {
            Log.w(TAG, "点赞节点未找到，回退到屏幕中央双击")
        }

        // 兜底：节点查找失败（抖音改版/渲染延迟/未拿到窗口），回退到屏幕中央双击
        Log.w(TAG, "点赞使用屏幕中央双击")
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val x = w * TAP_CENTER_X_RATIO
        val y = h * TAP_CENTER_Y_RATIO

        val path1 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, TAP_DURATION_MS)
        val path2 = Path().apply { moveTo(x, y) }
        val stroke2 = GestureDescription.StrokeDescription(
            path2,
            TAP_DURATION_MS + DOUBLE_TAP_GAP_MS,   // 第二次点击的起始时间偏移
            TAP_DURATION_MS
        )
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        return dispatchSyntheticGesture(DouyinAction.DoubleTap, gesture)
    }

    /** 点击评论按钮：优先用节点查找精准定位，失败则回退到硬编码坐标 */
    private fun tapCommentButton(): Boolean {
        // 优先通过无障碍节点查找评论按钮。
        // 抖音评论按钮的 contentDescription 通常包含"评论"（可能带数字如"评论 1234"）。
        // 找到后用其屏幕坐标点击，避免硬编码坐标在不同机型/抖音版本上偏移。
        val commentNode = findNodeByDescription(rootInActiveWindow, "评论")
        if (commentNode != null) {
            val rect = Rect()
            commentNode.getBoundsInScreen(rect)
            val x = rect.exactCenterX()
            val y = rect.exactCenterY()
            // 双重保险：findNodeByDescription 已过滤屏外节点，但某些机型 getBoundsInScreen
            // 返回延迟旧值，这里再检查一次，无效则回退到硬编码坐标
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            if (x in 0f..w && y in 0f..h) {
                Log.i(TAG, "评论节点定位成功: x=$x, y=$y, desc=${commentNode.contentDescription}")
                val path = Path().apply { moveTo(x, y) }
                val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                return dispatchSyntheticGesture(DouyinAction.ToggleComments, gesture)
            } else {
                Log.w(TAG, "评论节点坐标无效: x=$x, y=$y，回退到硬编码坐标")
            }
        } else {
            Log.w(TAG, "评论节点未找到，回退到硬编码坐标")
        }
        return tap(DouyinAction.ToggleComments, COMMENT_X_RATIO, COMMENT_Y_RATIO)
    }

    /** 关闭评论区：抖音评论区打开时，按返回键可关闭 */
    private fun closeComments(): Boolean {
        // performGlobalAction(GLOBAL_ACTION_BACK) 模拟系统返回键。
        // 抖音评论区打开时，返回键会关闭评论区回到视频；评论区未打开时返回键会退出抖音，
        // 所以这个动作只在评论区打开时使用（用户可把指向保持映射到关闭评论区）。
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.i(TAG, "关闭评论区: performGlobalAction(BACK)=$result")
        return result
    }

    private fun tap(action: DouyinAction, xRatio: Float, yRatio: Float): Boolean {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val x = w * xRatio
        val y = h * yRatio
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchSyntheticGesture(action, gesture)
    }

    private fun dispatchSyntheticGesture(
        action: DouyinAction,
        gesture: GestureDescription,
    ): Boolean = dispatchGesture(
        gesture,
        object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "手势执行完成: ${action.label}")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "手势执行取消: ${action.label}")
            }
        },
        null,
    )

    /** 调整媒体音量（抖音播放的是 STREAM_MUSIC） */
    private fun adjustVolume(up: Boolean): Boolean {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        Log.d(TAG, "volume ${if (up) "+" else "-"}")
        return true
    }

    // ---------- 无障碍节点查找辅助 ----------

    /**
     * 深度优先遍历节点树，找 [node] 及其子孙中 contentDescription 包含 [keyword]
     * **且 bounds 在屏幕可见区域内**的节点。
     *
     * 抖音信息流里每个视频项都有评论/点赞按钮，滚出屏幕的按钮 getBoundsInScreen
     * 会返回负坐标，传给 Path.moveTo 会抛 IllegalArgumentException。因此必须过滤掉
     * 屏幕外的节点，只返回当前可见的按钮。
     *
     * 注意：需要在 [gesture_service_config.xml] 里配置 canRetrieveWindowContent=true
     * 才能拿到 rootInActiveWindow，否则本方法始终返回 null。
     */
    private fun findNodeByDescription(
        node: AccessibilityNodeInfo?,
        keyword: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(keyword)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            // 只接受中心在屏幕可见区域内的节点，跳过滚出屏幕的视频项按钮
            if (cx in 0f..w && cy in 0f..h) {
                return node
            }
            // bounds 在屏幕外（滚出去的视频），继续搜索子节点找可见的匹配
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDescription(child, keyword)
            if (found != null) return found
        }
        return null
    }
}
