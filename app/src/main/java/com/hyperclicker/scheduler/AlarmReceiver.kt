package com.hyperclicker.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hyperclicker.ClickerApp
import com.hyperclicker.data.MODE_LOW_FREQ
import com.hyperclicker.service.ClickAccessibilityService
import com.hyperclicker.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 低频点击的闹钟触发接收器。
 * 闹钟唤醒进程后，把「点一次」交给无障碍服务执行；
 * 若服务尚未重连（进程刚被拉起），则做有界重试。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER = "com.hyperclicker.alarm.TRIGGER"
        const val ACTION_RETRY = "com.hyperclicker.alarm.RETRY"
        const val EXTRA_SCHEME_ID = "scheme_id"
        const val EXTRA_RETRY = "retry"
        private const val TAG = "HyperClicker"
        private const val MAX_RETRY = 4
        private const val RETRY_DELAY_MS = 5000L
        private const val REQ_RETRY = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val schemeId = intent.getLongExtra(EXTRA_SCHEME_ID, -1L)
        val retry = intent.getIntExtra(EXTRA_RETRY, 0)
        if (schemeId <= 0) return

        val service = ClickAccessibilityService.instance
        Log.i(TAG, "AlarmReceiver schemeId=$schemeId retry=$retry service=${service != null}")
        if (service != null) {
            service.triggerForScheme(schemeId)
            return
        }

        // 进程刚被闹钟拉起，无障碍服务尚未重连：
        // 先拉起保活前台服务，防止进程再次被杀，给无障碍服务重连留时间。
        KeepAliveService.start(context)
        if (retry < MAX_RETRY) {
            scheduleRetry(context, schemeId, retry + 1)
            return
        }

        // 重试耗尽仍未连接：只要方案仍启用，就重排下一次触发，保持链条不断
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ClickerApp
                val scheme = app.repository.getById(schemeId)
                if (scheme != null && scheme.mode == MODE_LOW_FREQ &&
                    scheme.enabled && scheme.points().isNotEmpty()
                ) {
                    AlarmScheduler.schedule(context, scheme)
                    Log.i(TAG, "AlarmReceiver 重试耗尽，已重排下一轮 schemeId=$schemeId")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AlarmReceiver 重排失败 schemeId=$schemeId", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun scheduleRetry(context: Context, schemeId: Long, retry: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_SCHEME_ID, schemeId)
            putExtra(EXTRA_RETRY, retry)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_RETRY + (schemeId % 100000).toInt(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = System.currentTimeMillis() + RETRY_DELAY_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
