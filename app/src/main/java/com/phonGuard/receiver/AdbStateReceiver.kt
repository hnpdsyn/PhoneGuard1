package com.phonGuard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ADB状态辅助接收器 - 监听电源状态变化
 */
class AdbStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 电源变化时，检查ADB状态
        val serviceIntent = Intent(context, com.phonGuard.service.AdbGuardService::class.java)
        context.startForegroundService(serviceIntent)
    }
}