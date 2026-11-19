package com.hyperclicker.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hyperclicker.ClickerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启：恢复所有「已启用 + 开机自启」的低频方案。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext as ClickerApp
        CoroutineScope(Dispatchers.IO).launch {
            val schemes = app.repository.getEnabledLowFreq()
            for (s in schemes) {
                if (s.autoStartOnBoot) {
                    AlarmScheduler.schedule(context, s)
                }
            }
        }
    }
}
