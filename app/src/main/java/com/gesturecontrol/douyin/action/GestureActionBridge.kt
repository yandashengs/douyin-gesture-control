package com.gesturecontrol.douyin.action

import android.util.Log

/**
 * 手势识别端 ↔ 无障碍服务端的桥接单例。
 *
 * - AccessibilityService 启动时 [bind]，销毁时 [unbind]
 * - 摄像头识别线程识别到动作后调用 [dispatch]
 *
 * 这样两边互不持有引用，生命周期也清晰。
 */
object GestureActionBridge {
    private const val TAG = "GestureActionBridge"

    @Volatile
    private var service: ActionExecutor? = null

    private val tracker = AcceptedActionTracker()

    /** 最近一次执行的动作（供 UI 显示） */
    val lastAction: DouyinAction? get() = tracker.lastAction

    /** 最近一次动作的时间戳（System.currentTimeMillis） */
    val lastActionTime: Long get() = tracker.lastActionTime

    fun bind(executor: ActionExecutor) {
        service = executor
        Log.i(TAG, "ActionExecutor 已绑定")
    }

    fun unbind() {
        service = null
        Log.i(TAG, "ActionExecutor 已解绑")
    }

    fun isReady(): Boolean = service != null

    /**
     * 派发一个动作。如果没有可用的无障碍服务，会被丢弃并打日志。
     * @return 是否成功派发
     */
    fun dispatch(action: DouyinAction): Boolean {
        val target = service ?: run {
            Log.w(TAG, "无障碍服务未就绪，丢弃动作: ${action.label}")
            return false
        }
        return try {
            val accepted = target.execute(action)
            if (!accepted) {
                Log.w(TAG, "系统未接受动作: ${action.label}")
            }
            tracker.record(action, accepted)
        } catch (t: Throwable) {
            Log.e(TAG, "执行动作失败: ${action.label}", t)
            false
        }
    }
}

/** 由 [DouyinGestureService] 实现 */
interface ActionExecutor {
    fun execute(action: DouyinAction): Boolean
}
