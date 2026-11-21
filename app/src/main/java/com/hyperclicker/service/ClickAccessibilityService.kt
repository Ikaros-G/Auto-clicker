package com.hyperclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.hyperclicker.ClickerApp
import com.hyperclicker.R
import com.hyperclicker.data.ClickPoint
import com.hyperclicker.data.ClickScheme
import com.hyperclicker.data.MODE_HIGH_FREQ
import com.hyperclicker.data.MODE_LOW_FREQ
import com.hyperclicker.data.REPEAT_COUNT
import com.hyperclicker.data.REPEAT_DURATION
import com.hyperclicker.scheduler.AlarmScheduler
import com.hyperclicker.ui.PointPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 连点器核心无障碍服务：
 * 1. 通过 dispatchGesture 注入点击手势（无需 root）；
 * 2. 高频点击用协程循环实现；
 * 3. 低频点击通过 AlarmManager 触发后，由本服务执行单次点击序列；
 * 4. 同时承载悬浮控制窗（录点 / 开始 / 停止 / 隐藏）。
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HyperClicker"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app: ClickerApp get() = application as ClickerApp

    // 悬浮窗
    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var recording = false

    // 悬浮窗拖拽状态
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLpX = 0
    private var downLpY = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ClickerState.accessibilityConnected.value = true
        Log.i(TAG, "无障碍服务已连接，屏幕=${screenSize()}")
        showFloatingPanel()
        // 服务重连后，恢复所有已启用的低频方案闹钟（服务被停用期间闹钟链会断裂）
        scope.launch {
            app.repository.getEnabledLowFreq().forEach { scheme ->
                AlarmScheduler.schedule(this@ClickAccessibilityService, scheme)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本应用不读取窗口内容，事件仅用于保持服务存活
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onDestroy() {
        instance = null
        ClickerState.accessibilityConnected.value = false
        Log.i(TAG, "无障碍服务已断开")
        ClickerState.running.value = false
        KeepAliveService.stop(this)
        scope.cancel()
        removeFloatingPanel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // 对外控制接口（主界面 / 悬浮球 / 闹钟接收器直接调用）
    // ---------------------------------------------------------------

    /** 仅选中方案（不启动），供悬浮球「录点」确定写入目标 */
    fun selectScheme(id: Long) {
        scope.launch {
            val scheme = app.repository.getById(id) ?: return@launch
            ClickerState.activeSchemeId.value = scheme.id
            ClickerState.activeSchemeName.value = scheme.name
            updatePanel()
        }
    }

    /** 启动方案：高频 -> 开启循环；低频 -> 排程闹钟 */
    fun startScheme(id: Long) {
        scope.launch {
            val scheme = app.repository.getById(id) ?: return@launch
            val points = scheme.points()
            if (points.isEmpty()) {
                toast("请先通过悬浮球「录点」添加点击位置")
                return@launch
            }
            Log.i(TAG, "startScheme id=$id mode=${scheme.mode} enabled=${scheme.enabled} points=${points.size}")
            stopCurrent(exceptId = scheme.id)
            ClickerState.activeSchemeId.value = scheme.id
            ClickerState.activeSchemeName.value = scheme.name
            ClickerState.running.value = true
            updatePanel()

            when (scheme.mode) {
                MODE_HIGH_FREQ -> {
                    runHighFreqLoop(scheme, points) // 挂起直到循环结束
                    ClickerState.running.value = false
                    updatePanel()
                }
                MODE_LOW_FREQ -> {
                    app.repository.update(scheme.copy(enabled = true))
                    KeepAliveService.start(this@ClickAccessibilityService)
                    val (w, h) = screenSize()
                    val ok = performSequence(points, w, h)
                    AlarmScheduler.schedule(this@ClickAccessibilityService, scheme)
                    toast(
                        if (ok) "已开启：立即点击一次，之后每 ${formatInterval(scheme.intervalMs)} 一次"
                        else "已排程，但立即点击失败：请重新开关一次无障碍服务"
                    )
                }
            }
        }
    }

    /**
     * 停止当前方案。
     * [exceptId] 供 startScheme 使用：启动目标方案时跳过「停用目标方案」这一步，
     * 否则 stopCurrent 的 enabled=false 会与随后 startScheme 的 enabled=true 竞争，
     * 导致低频方案刚启动就被关掉——表现为「只有第一遍有效」。
     */
    fun stopCurrent(exceptId: Long = -1L) {
        val id = ClickerState.activeSchemeId.value
        Log.i(TAG, "stopCurrent id=$id exceptId=$exceptId")
        if (id > 0 && id != exceptId) {
            scope.launch {
                app.repository.getById(id)?.let { s ->
                    if (s.mode == MODE_LOW_FREQ && s.enabled) {
                        app.repository.update(s.copy(enabled = false))
                        Log.i(TAG, "stopCurrent 停用方案 id=$id")
                    }
                }
            }
            AlarmScheduler.cancel(this, id)
        }
        ClickerState.running.value = false
        KeepAliveService.stop(this)
        updatePanel()
    }

    /**
     * 低频点击触发入口（AlarmReceiver 调用）。
     * 执行一次完整点击序列，然后排定下一次闹钟。
     */
    fun triggerForScheme(id: Long) {
        scope.launch {
            val scheme = app.repository.getById(id) ?: return@launch
            Log.i(TAG, "triggerForScheme id=$id mode=${scheme.mode} enabled=${scheme.enabled}")
            if (scheme.mode != MODE_LOW_FREQ || !scheme.enabled) return@launch
            val points = scheme.points()
            if (points.isEmpty()) return@launch
            val (w, h) = screenSize()
            val ok = performSequence(points, w, h)
            Log.i(TAG, "triggerForScheme performSequence ok=$ok，已重排下一次")
            if (!ok) {
                toast("点击注入失败：请重新开关一次无障碍服务后再试")
            }
            AlarmScheduler.schedule(this@ClickAccessibilityService, scheme)
        }
    }

    /** 测试手势注入：点击屏幕中心，用于诊断无障碍服务是否真的能注入点击 */
    fun testClick() {
        scope.launch {
            val (w, h) = screenSize()
            val cx = (w / 2).toFloat()
            val cy = (h / 2).toFloat()
            val ok = dispatchClick(cx, cy, 60L)
            Log.i(TAG, "testClick center=($cx,$cy) ok=$ok")
            toast(if (ok) "测试点击已发送（屏幕中心 $cx,$cy）" else "测试点击失败：请重新开关无障碍服务后再试")
        }
    }

    // ---------------------------------------------------------------
    // 高频循环
    // ---------------------------------------------------------------

    private suspend fun runHighFreqLoop(scheme: ClickScheme, points: List<ClickPoint>) {
        val (w, h) = screenSize()
        val loopStart = SystemClock.elapsedRealtime()
        var count = 0
        var firstFailureNotified = false
        while (ClickerState.running.value) {
            if (scheme.repeatType == REPEAT_COUNT && count >= scheme.repeatValue) break
            if (scheme.repeatType == REPEAT_DURATION &&
                SystemClock.elapsedRealtime() - loopStart >= scheme.repeatValue * 1000L
            ) break
            val ok = performSequence(points, w, h)
            if (!ok && !firstFailureNotified) {
                firstFailureNotified = true
                toast("点击注入失败：请重新开关一次无障碍服务后再试")
            }
            count++
            delay(scheme.intervalMs)
        }
    }

    /** 按顺序执行一次完整点击序列；最后一个点位的间隔无意义，跳过 */
    private suspend fun performSequence(points: List<ClickPoint>, w: Int, h: Int): Boolean {
        var allOk = true
        for ((i, p) in points.withIndex()) {
            val px = (p.x * w).toInt().coerceIn(0, w - 1)
            val py = (p.y * h).toInt().coerceIn(0, h - 1)
            if (!dispatchClick(px.toFloat(), py.toFloat(), p.pressMs)) allOk = false
            if (p.gapMs > 0 && i < points.size - 1) delay(p.gapMs)
        }
        return allOk
    }

    private fun dispatchClick(x: Float, y: Float, pressMs: Long): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, pressMs.coerceAtLeast(10))
                )
                .build()
            val accepted = dispatchGesture(gesture, null, null)
            if (!accepted) Log.w(TAG, "dispatchGesture 返回 false @ ($x,$y)")
            accepted
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchGesture 异常 @ ($x,$y)", t)
            false
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
    }

    // ---------------------------------------------------------------
    // 录点
    // ---------------------------------------------------------------

    private fun startRecording() {
        if (recording) return
        recording = true
        scope.launch {
            val id = ClickerState.activeSchemeId.value
            val existing = if (id > 0) app.repository.getById(id)?.points().orEmpty() else emptyList()
            PointPicker(
                context = this@ClickAccessibilityService,
                existingPoints = existing,
                onDone = { points ->
                    recording = false
                    savePointsToActiveScheme(points)
                },
                onCancel = { recording = false }
            ).show()
        }
    }

    private fun savePointsToActiveScheme(points: List<ClickPoint>) {
        if (points.isEmpty()) return
        val id = ClickerState.activeSchemeId.value
        if (id <= 0) {
            toast("请先在主界面选择一个方案，再录点")
            return
        }
        scope.launch {
            val scheme = app.repository.getById(id) ?: return@launch
            val merged = scheme.points() + points
            app.repository.update(scheme.copy(pointsJson = ClickScheme.toJson(merged)))
            toast("已添加 ${points.size} 个点位（共 ${merged.size} 个）")
        }
    }

    // ---------------------------------------------------------------
    // 悬浮控制窗
    // ---------------------------------------------------------------

    private fun showFloatingPanel() {
        if (panelView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 80
        lp.y = 220

        view.findViewById<Button>(R.id.btn_record).setOnClickListener { startRecording() }
        view.findViewById<Button>(R.id.btn_start).setOnClickListener { onPanelStart() }
        view.findViewById<Button>(R.id.btn_stop).setOnClickListener { stopCurrent() }
        view.findViewById<Button>(R.id.btn_test).setOnClickListener { testClick() }
        view.findViewById<Button>(R.id.btn_hide).setOnClickListener { removeFloatingPanel() }

        val title = view.findViewById<TextView>(R.id.panel_title)
        title.setOnTouchListener { _, ev -> onDrag(ev, lp, wm) }

        wm.addView(view, lp)
        panelView = view
        layoutParams = lp
        updatePanel()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun onDrag(
        event: MotionEvent,
        lp: WindowManager.LayoutParams,
        wm: WindowManager
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downLpX = lp.x
                downLpY = lp.y
            }
            MotionEvent.ACTION_MOVE -> {
                lp.x = downLpX + (event.rawX - downRawX).toInt()
                lp.y = downLpY + (event.rawY - downRawY).toInt()
                wm.updateViewLayout(panelView, lp)
            }
        }
        return true
    }

    private fun onPanelStart() {
        val id = ClickerState.activeSchemeId.value
        if (id <= 0) {
            toast("请先在主界面选择一个方案")
            return
        }
        startScheme(id)
    }

    private fun updatePanel() {
        val title = panelView?.findViewById<TextView>(R.id.panel_title) ?: return
        val status = panelView?.findViewById<TextView>(R.id.panel_status) ?: return
        val name = ClickerState.activeSchemeName.value
        title.text = if (name.isBlank()) "连点器" else name
        status.text = if (ClickerState.running.value) "运行中" else "已停止"
    }

    private fun removeFloatingPanel() {
        panelView?.let { windowManager?.removeView(it) }
        panelView = null
        layoutParams = null
        windowManager = null
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun formatInterval(ms: Long): String = when {
        ms >= 3_600_000 && ms % 3_600_000 == 0L -> "${ms / 3_600_000} 小时"
        ms >= 60_000 && ms % 60_000 == 0L -> "${ms / 60_000} 分钟"
        ms >= 1_000 && ms % 1_000 == 0L -> "${ms / 1_000} 秒"
        else -> "$ms 毫秒"
    }
}
