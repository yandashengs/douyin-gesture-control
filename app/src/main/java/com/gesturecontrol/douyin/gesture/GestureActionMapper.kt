package com.gesturecontrol.douyin.gesture

import android.content.Context
import com.gesturecontrol.douyin.action.DouyinAction

/**
 * 把识别端的 [GestureEvent] 映射为抖音可执行的 [DouyinAction]。
 *
 * 改造后：不再写死 when 分支，而是查 [MappingRepository] 里用户保存的配置。
 * 这样用户在配置页改完保存后，立即对识别端生效。
 *
 * 使用方式：
 *   val mapper = GestureActionMapper.from(context)
 *   val action = mapper.map(event)
 *   action?.let { GestureActionBridge.dispatch(it) }
 */
class GestureActionMapper private constructor(
    private val repository: MappingRepository,
) {
    /** 把手势事件映射为抖音动作；返回 null 表示无映射或被禁用 */
    fun map(event: GestureEvent): DouyinAction? = repository.actionFor(event)

    /** 暴露 repository 供 UI 读写配置 */
    val repo: MappingRepository get() = repository

    companion object {
        @Volatile
        private var instance: GestureActionMapper? = null

        fun from(context: Context): GestureActionMapper =
            instance ?: synchronized(this) {
                instance ?: GestureActionMapper(MappingRepository.get(context)).also { instance = it }
            }
    }
}
