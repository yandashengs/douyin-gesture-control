package com.gesturecontrol.douyin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.douyin.action.DouyinAction
import com.gesturecontrol.douyin.gesture.GestureActionMapper
import com.gesturecontrol.douyin.gesture.MappingRepository
import com.gesturecontrol.douyin.gesture.PresetGesture
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手势映射配置页的 ViewModel。
 *
 * 持有当前 [MappingRepository] 的快照，UI 改完调用 [set] 立即保存并刷新 StateFlow。
 */
class MappingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: MappingRepository = GestureActionMapper.from(app.applicationContext).repo

    private val _mapping = MutableStateFlow(repo.current())
    val mapping: StateFlow<Map<PresetGesture, DouyinAction>> = _mapping.asStateFlow()

    fun set(gesture: PresetGesture, action: DouyinAction) {
        repo.set(gesture, action)
        _mapping.value = repo.current()
    }

    fun resetToDefault() {
        repo.resetToDefault()
        _mapping.value = repo.current()
    }
}
