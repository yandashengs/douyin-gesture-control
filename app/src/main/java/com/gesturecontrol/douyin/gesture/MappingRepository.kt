package com.gesturecontrol.douyin.gesture

import android.content.Context
import android.content.SharedPreferences
import com.gesturecontrol.douyin.action.DouyinAction
import com.gesturecontrol.douyin.action.id

/**
 * 手势→动作 映射的持久化仓库。
 *
 * - 用 SharedPreferences 存「手势 ID → 动作 ID」
 * - 首次启动用 [PresetGesture.defaultMapping] 初始化
 * - 改完后调用 [save] 立即生效（GestureActionMapper 实时读取内存缓存）
 *
 * 线程安全：[load] 和 [save] 都加 synchronized。
 */
class MappingRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 内存缓存，避免每次映射都读 SharedPreferences */
    @Volatile
    private var cache: Map<PresetGesture, DouyinAction> = emptyMap()

    init {
        load()
    }

    /** 从磁盘加载映射；若为空则写入默认值 */
    fun load(): Map<PresetGesture, DouyinAction> = synchronized(this) {
        val loaded = PresetGesture.entries.mapNotNull { g ->
            val actionId = prefs.getString(g.id, null) ?: return@mapNotNull null
            g to DouyinAction.fromId(actionId)
        }.toMap()

        // 首次启动：写入默认值
        cache = if (loaded.isEmpty()) {
            PresetGesture.defaultMapping.also { default ->
                prefs.edit().apply {
                    default.forEach { (g, a) -> putString(g.id, a.id()) }
                    apply()
                }
            }
        } else {
            // 补全缺失的手势（升级时新增的预设手势没存过）
            PresetGesture.defaultMapping.mapValues { (g, defaultAction) ->
                loaded[g] ?: defaultAction
            }
        }
        cache
    }

    /** 保存整套映射并刷新内存缓存 */
    fun save(mapping: Map<PresetGesture, DouyinAction>) = synchronized(this) {
        prefs.edit().apply {
            mapping.forEach { (g, a) -> putString(g.id, a.id()) }
            apply()
        }
        cache = mapping
    }

    /** 修改单个手势的映射并保存 */
    fun set(gesture: PresetGesture, action: DouyinAction) = synchronized(this) {
        val updated = cache.toMutableMap().apply { this[gesture] = action }
        save(updated)
    }

    /** 当前生效的映射（不要修改返回值） */
    fun current(): Map<PresetGesture, DouyinAction> = cache

    /** 根据事件查动作（供 GestureActionMapper 使用） */
    fun actionFor(event: GestureEvent): DouyinAction? {
        val gesture = PresetGesture.entries.firstOrNull { it.event == event } ?: return null
        return cache[gesture]?.takeIf { it !is DouyinAction.None }
    }

    /** 重置为默认映射 */
    fun resetToDefault() = synchronized(this) {
        prefs.edit().clear().apply()
        load()
    }

    companion object {
        private const val PREFS_NAME = "gesture_mapping"

        @Volatile
        private var instance: MappingRepository? = null

        fun get(context: Context): MappingRepository =
            instance ?: synchronized(this) {
                instance ?: MappingRepository(context).also { instance = it }
            }
    }
}
