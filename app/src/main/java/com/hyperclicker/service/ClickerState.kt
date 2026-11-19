package com.hyperclicker.service

import com.hyperclicker.data.ClickScheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 跨组件共享的运行状态（内存态）。
 * 无障碍服务与主界面同进程，直接读写此单例即可。
 */
object ClickerState {
    /** 无障碍服务是否已连接 */
    val accessibilityConnected = MutableStateFlow(false)

    /** 当前激活的方案 id（悬浮球「开始/停止」针对它） */
    val activeSchemeId = MutableStateFlow(-1L)

    /** 当前激活方案名（悬浮球显示用） */
    val activeSchemeName = MutableStateFlow("")

    /** 当前是否处于运行中（高频循环中 / 低频已排程） */
    val running = MutableStateFlow(false)
}
