package com.hyperclicker.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hyperclicker.data.ClickScheme

/**
 * 低频点击的调度器。
 * 低频点击不能用协程 delay 实现（进程被杀/Doze 会中断），
 * 必须用 AlarmManager 的精确闹钟 + 开机自启恢复。
 */
object AlarmScheduler {
    private const val REQ_TRIGGER = 1000

    /** 为某个低频方案排定下一次触发（now + intervalMs） */
    fun schedule(context: Context, scheme: ClickScheme) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = triggerPendingIntent(context, scheme.id)
        val triggerAt = System.currentTimeMillis() + scheme.intervalMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 取消某方案的闹钟 */
    fun cancel(context: Context, schemeId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(triggerPendingIntent(context, schemeId))
    }

    private fun triggerPendingIntent(context: Context, schemeId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
            putExtra(AlarmReceiver.EXTRA_SCHEME_ID, schemeId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_TRIGGER + (schemeId % 100000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
