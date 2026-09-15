package com.phonGuard.adbguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.adbguard.AdbGuardApp
import com.phonGuard.adbguard.service.AdbGuardService

/**
 * ADB状态辅助接收器 - 监听电源状态变化
 */
class AdbStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 电源变化时（可能接入电脑USB），检查ADB状态
        val app = context.applicationContext as AdbGuardApp
        if (!app.configManager.adbGuardEnabled) return
        val serviceIntent = Intent(context, AdbGuardService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
