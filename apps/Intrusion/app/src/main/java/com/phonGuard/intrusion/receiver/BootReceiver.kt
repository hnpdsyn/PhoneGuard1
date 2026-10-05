package com.phonGuard.intrusion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.intrusion.IntrusionApp
import com.phonGuard.intrusion.service.IntrusionDetectorService

/**
 * 开机自启动接收器 - 手机重启后自动启动入侵检测服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as IntrusionApp
            val config = app.configManager

            // 延迟启动，等待系统完全加载
            Thread.sleep(10000)

            if (config.intrusionEnabled) {
                context.startForegroundService(Intent(context, IntrusionDetectorService::class.java))
            }
        }
    }
}
