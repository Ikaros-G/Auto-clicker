package com.hyperclicker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/** 高频连点（毫秒/秒级） */
const val MODE_HIGH_FREQ = 0

/** 低频点击（分钟/小时级，如每 15 分钟一次） */
const val MODE_LOW_FREQ = 1

/** 无限循环 */
const val REPEAT_INFINITE = 0

/** 固定次数 */
const val REPEAT_COUNT = 1

/** 固定时长（秒） */
const val REPEAT_DURATION = 2

/**
 * 单个点击点，坐标使用归一化值（0.0 ~ 1.0，相对全屏宽高），
 * 这样在不同分辨率 / 横竖屏下都能还原到一致的位置。
 */
data class ClickPoint(
    val x: Float,
    val y: Float,
    val order: Int = 0,
    val pressMs: Long = 40,   // 按压时长（毫秒），支持长按
    val gapMs: Long = 0       // 该点点击后额外等待时间（毫秒）
)

@Entity(tableName = "schemes")
data class ClickScheme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: Int = MODE_HIGH_FREQ,
    val intervalMs: Long = 100,          // 统一用毫秒存储：15min = 900000
    val repeatType: Int = REPEAT_INFINITE,
    val repeatValue: Int = 0,            // 次数 或 秒数
    val autoStartOnBoot: Boolean = false,
    val enabled: Boolean = true,
    val pointsJson: String = "[]"
) {
    fun points(): List<ClickPoint> = parsePoints(pointsJson)

    companion object {
        fun parsePoints(json: String): List<ClickPoint> {
            return try {
                val arr = JSONArray(json)
                val list = mutableListOf<ClickPoint>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        ClickPoint(
                            x = o.getDouble("x").toFloat(),
                            y = o.getDouble("y").toFloat(),
                            order = o.optInt("order", i),
                            pressMs = o.optLong("pressMs", 40),
                            gapMs = o.optLong("gapMs", 0)
                        )
                    )
                }
                list.sortedBy { it.order }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun toJson(points: List<ClickPoint>): String {
            val arr = JSONArray()
            points.forEachIndexed { i, p ->
                val o = JSONObject()
                o.put("x", p.x.toDouble())
                o.put("y", p.y.toDouble())
                o.put("order", i)
                o.put("pressMs", p.pressMs)
                o.put("gapMs", p.gapMs)
                arr.put(o)
            }
            return arr.toString()
        }
    }
}
