package com.hyperclicker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperclicker.ClickerApp
import com.hyperclicker.data.ClickScheme
import com.hyperclicker.service.ClickAccessibilityService
import com.hyperclicker.service.ClickerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as ClickerApp).repository

    val schemes: StateFlow<List<ClickScheme>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accessibilityConnected = ClickerState.accessibilityConnected
    val running = ClickerState.running
    val activeSchemeId = ClickerState.activeSchemeId

    suspend fun getScheme(id: Long): ClickScheme? = repository.getById(id)

    suspend fun save(scheme: ClickScheme): Long =
        if (scheme.id == 0L) repository.insert(scheme)
        else {
            repository.update(scheme)
            scheme.id
        }

    fun delete(scheme: ClickScheme) {
        viewModelScope.launch {
            if (ClickerState.activeSchemeId.value == scheme.id) {
                ClickAccessibilityService.instance?.stopCurrent()
            }
            repository.delete(scheme)
        }
    }

    fun select(scheme: ClickScheme) {
        ClickAccessibilityService.instance?.selectScheme(scheme.id)
    }

    fun start(scheme: ClickScheme) {
        // 无障碍未开启时由 UI 层提示
        ClickAccessibilityService.instance?.startScheme(scheme.id)
    }

    fun stop() {
        ClickAccessibilityService.instance?.stopCurrent()
    }

    fun clearPoints(id: Long) {
        viewModelScope.launch {
            val s = repository.getById(id) ?: return@launch
            repository.update(s.copy(pointsJson = ClickScheme.toJson(emptyList())))
        }
    }
}
