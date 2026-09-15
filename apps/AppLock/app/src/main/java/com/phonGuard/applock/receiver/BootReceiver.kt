package com.phonGuard.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.applock.AppLockApp
import com.phonGuard.applock.service.AppLockService

/**
 * 开机自启动接收器 - 手机重启后自动启动应用锁服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as AppLockApp
            val config = app.configManager

            // 延迟启动，等待系统完全加载
            Thread.sleep(10000)

            if (config.appLockEnabled) {
                context.startForegroundService(Intent(context, AppLockService::class.java))
            }
        }
    }
}
