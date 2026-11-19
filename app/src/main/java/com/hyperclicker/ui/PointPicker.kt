package com.hyperclicker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.hyperclicker.data.ClickPoint

/**
 * 全屏半透明点位采集层。
 * 用户切到目标界面后，通过悬浮球「录点」触发本覆盖层：
 * - 快速点击 = 普通点击点位（pressMs=40）；
 * - 长按（>=500ms）= 长按点位，pressMs 记录实际按住时长；
 * 底部栏支持 取消 / 撤销 / 清空 / 完成。
 */
class PointPicker(
    private val context: Context,
    private val existingPoints: List<ClickPoint> = emptyList(),
    private val onDone: (List<ClickPoint>) -> Unit,
    private val onCancel: () -> Unit = {}
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var picker: PickerView? = null

    fun show() {
        if (picker != null) return
        val v = PickerView(context, existingPoints)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        wm.addView(v, lp)
        picker = v
    }

    private fun close() {
        picker?.let { wm.removeView(it) }
        picker = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private inner class PickerView(
        context: Context,
        private val existingPoints: List<ClickPoint>
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val points = mutableListOf<ClickPoint>()

        private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5252")
            style = Paint.Style.FILL
        }
        private val longPressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA000")
            style = Paint.Style.FILL
        }
        private val existingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#42A5F5")
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * density
            textAlign = Paint.Align.CENTER
        }
        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE0B2")
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC000000.toInt()
        }

        private var w = 0
        private var h = 0

        private var downTime = 0L
        private var downX = 0f
        private var downY = 0f

        private fun topBarH() = 56f * density
        private fun bottomBarH() = 72f * density

        override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
            w = width
            h = height
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0x26000000)

            canvas.drawRect(0f, 0f, w.toFloat(), topBarH(), barPaint)
            val hint = if (existingPoints.isEmpty()) {
                "点击=普通点 · 长按=长按点 · 点顶部取消"
            } else {
                "蓝=已有点 · 红/橙=本次新增 · 点顶部取消"
            }
            canvas.drawText(hint, w / 2f, topBarH() / 2f + 6f * density, textPaint)

            val top = h - bottomBarH()
            canvas.drawRect(0f, top, w.toFloat(), h.toFloat(), barPaint)
            val labels = listOf("取消", "撤销", "清空", "完成")
            val slotW = w / 4f
            for (i in labels.indices) {
                canvas.drawText(labels[i], slotW * i + slotW / 2f, top + bottomBarH() / 2f + 6f * density, textPaint)
            }

            for ((i, p) in existingPoints.withIndex()) {
                val cx = p.x * w
                val cy = p.y * h
                canvas.drawCircle(cx, cy, 24f * density, existingPaint)
                canvas.drawCircle(cx, cy, 24f * density, ringPaint)
                canvas.drawText("${i + 1}", cx, cy + 5f * density, textPaint)
                if (p.pressMs >= LONG_PRESS_MS) {
                    canvas.drawText("长按 ${p.pressMs}ms", cx, cy + 40f * density, subTextPaint)
                }
            }

            for ((i, p) in points.withIndex()) {
                val cx = p.x * w
                val cy = p.y * h
                val dot = if (p.pressMs >= LONG_PRESS_MS) longPressPaint else normalPaint
                canvas.drawCircle(cx, cy, 24f * density, dot)
                canvas.drawCircle(cx, cy, 24f * density, ringPaint)
                canvas.drawText("${existingPoints.size + i + 1}", cx, cy + 5f * density, textPaint)
                if (p.pressMs >= LONG_PRESS_MS) {
                    canvas.drawText("长按 ${p.pressMs}ms", cx, cy + 40f * density, subTextPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = SystemClock.uptimeMillis()
                    downX = event.x
                    downY = event.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val holdMs = SystemClock.uptimeMillis() - downTime
                    val x = event.x
                    val y = event.y
                    when {
                        y < topBarH() -> {
                            onCancel()
                            close()
                        }
                        y > h - bottomBarH() -> handleBottomBar(x)
                        else -> {
                            val pressMs = if (holdMs >= LONG_PRESS_MS) holdMs.coerceAtMost(60_000L) else 40L
                            points.add(ClickPoint(downX / w, downY / h, order = points.size, pressMs = pressMs))
                            invalidate()
                        }
                    }
                    return true
                }
                else -> return true
            }
        }

        private fun handleBottomBar(x: Float) {
            val slotW = w / 4f
            when {
                x < slotW -> {
                    onCancel()
                    close()
                }
                x < 2 * slotW -> {
                    if (points.isNotEmpty()) {
                        points.removeAt(points.size - 1)
                        invalidate()
                    }
                }
                x < 3 * slotW -> {
                    points.clear()
                    invalidate()
                }
                else -> {
                    onDone(points.toList())
                    close()
                }
            }
        }
    }

    companion object {
        /** 按住超过该时长即视为长按点位 */
        private const val LONG_PRESS_MS = 500L
    }
}
