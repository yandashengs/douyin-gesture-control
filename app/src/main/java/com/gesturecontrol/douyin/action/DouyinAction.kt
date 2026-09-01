package com.gesturecontrol.douyin.action

/**
 * 抖音可执行的操作。
 * 把"业务操作"和"手势识别"解耦：识别端产出 [com.gesturecontrol.douyin.gesture.GestureEvent]，
 * 再由 GestureActionMapper 映射成这里的 DouyinAction，最后由 AccessibilityService 执行。
 */
sealed class DouyinAction(val label: String) {
    /** 屏幕从下往上滑 → 切到下一个视频 */
    object SwipeUp : DouyinAction("下一个视频")

    /** 屏幕从上往下滑 → 回到上一个视频 */
    object SwipeDown : DouyinAction("上一个视频")

    /** 屏幕中央双击 → 点赞 */
    object DoubleTap : DouyinAction("点赞")

    /** 点击右侧评论按钮位置 → 打开/关闭评论区 */
    object ToggleComments : DouyinAction("评论区")

    /** 关闭评论区（按返回键，抖音评论区打开时有效） */
    object CloseComments : DouyinAction("关闭评论区")

    /** 单击屏幕中央 → 暂停/播放 */
    object TogglePause : DouyinAction("暂停/播放")

    /** 媒体音量+ */
    object VolumeUp : DouyinAction("音量+")

    /** 媒体音量- */
    object VolumeDown : DouyinAction("音量-")

    /** 禁用 - 不触发任何动作。用户可在配置页把手势映射到此值以禁用该手势 */
    object None : DouyinAction("禁用")

    companion object {
        /** 所有可用动作（用于配置页下拉选择），None 放最后。
         *  filterNotNull 防御 sealed class object 初始化时序导致的 null。 */
        val all: List<DouyinAction> = listOf(
            SwipeUp, SwipeDown, DoubleTap, TogglePause, ToggleComments, CloseComments,
            VolumeUp, VolumeDown, None,
        ).filterNotNull()

        fun fromId(id: String): DouyinAction = when (id) {
            "swipe_up" -> SwipeUp
            "swipe_down" -> SwipeDown
            "double_tap" -> DoubleTap
            "toggle_pause" -> TogglePause
            "toggle_comments" -> ToggleComments
            "close_comments" -> CloseComments
            "volume_up" -> VolumeUp
            "volume_down" -> VolumeDown
            "none" -> None
            else -> None
        }
    }
}

/** 动作 → 持久化 ID（供 MappingRepository 用） */
fun DouyinAction.id(): String = when (this) {
    DouyinAction.SwipeUp -> "swipe_up"
    DouyinAction.SwipeDown -> "swipe_down"
    DouyinAction.DoubleTap -> "double_tap"
    DouyinAction.ToggleComments -> "toggle_comments"
    DouyinAction.CloseComments -> "close_comments"
    DouyinAction.TogglePause -> "toggle_pause"
    DouyinAction.VolumeUp -> "volume_up"
    DouyinAction.VolumeDown -> "volume_down"
    DouyinAction.None -> "none"
}
