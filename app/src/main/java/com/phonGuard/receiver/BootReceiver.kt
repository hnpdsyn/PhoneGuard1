package com.phonGuard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.PhoneGuardApp
import com.phonGuard.service.*

/**
 * 开机自启动接收器 - 手机重启后自动启动所有已启用的服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as PhoneGuardApp
            val config = app.configManager

            if (!config.allServicesEnabled) return

            // 延迟启动，等待系统完全加载
            Thread.sleep(10000)

            if (config.appLockEnabled) {
                context.startForegroundService(Intent(context, AppLockService::class.java))
            }
            if (config.adbGuardEnabled) {
                context.startForegroundService(Intent(context, AdbGuardService::class.java))
            }
            if (config.intrusionEnabled) {
                context.startForegroundService(Intent(context, IntrusionDetectorService::class.java))
            }
            if (config.simGuardEnabled) {
                context.startForegroundService(Intent(context, SimGuardService::class.java))
            }
            if (config.loginGuardEnabled) {
                context.startForegroundService(Intent(context, LoginGuardService::class.java))
            }
        }
    }
}