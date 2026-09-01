package com.gesturecontrol.douyin.gesture

/**
 * 识别端输出的"手势事件"。与时序检测解耦的语义事件。
 * 由 [GestureDetector] 产出，再经 [com.gesturecontrol.douyin.gesture.GestureActionMapper] 映射为
 * [com.gesturecontrol.douyin.action.DouyinAction]。
 */
sealed class GestureEvent(val label: String) {
    /** 无有效手势 */
    object None : GestureEvent("无")

    /** 手掌向上挥动 */
    object SwipeUp : GestureEvent("上挥")

    /** 手掌向下挥动 */
    object SwipeDown : GestureEvent("下挥")

    /** 握拳后快速张开 → 双击点赞 */
    object FistThenOpen : GestureEvent("握拳张开")

    /** 食指+中指伸出(V字)保持 → 评论区 */
    object VSignHold : GestureEvent("V字保持")

    /** 拇指食指距离变大 → 音量加 */
    object PinchExpand : GestureEvent("捏合张开")

    /** 拇指食指距离变小 → 音量减 */
    object PinchClose : GestureEvent("捏合收缩")

    /** 张开手掌保持不动 → 暂停/播放 */
    object PalmHold : GestureEvent("手掌保持")

    /** 仅食指伸出保持 → 用户可配置 */
    object PointHold : GestureEvent("指向保持")

    /** OK 手势（拇指食指成圈，中指无名指小指伸直） → 用户可配置 */
    object OkSign : GestureEvent("OK手势")

    /** 摇滚手势（食指+小指伸出，中指无名指弯曲） → 用户可配置 */
    object RockSign : GestureEvent("摇滚手势")
}
