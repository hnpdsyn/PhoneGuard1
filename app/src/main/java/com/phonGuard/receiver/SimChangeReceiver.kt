package com.phonGuard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.NotificationHelper
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.*

/**
 * SIM卡状态变化广播接收器
 */
class SimChangeReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.SIM_STATE_CHANGED") {
            val state = intent.getStringExtra("ss")
            if (state == "ABSENT" || state == "LOADED") {
                // SIM卡被拔出或加载
                val app = context.applicationContext as PhoneGuardApp
                val config = app.configManager
                if (!config.simGuardEnabled) return

                val logger = app.securityLogger
                scope.launch {
                    logger.logEvent(
                        SecurityLogger.SecurityEvent(
                            type = "sim",
                            subType = "state_changed",
                            message = "SIM卡状态变化: $state",
                            severity = 1
                        )
                    )
                }

                // 如果SIM卡刚加载，检查是否与原卡一致
                if (state == "LOADED") {
                    // 立即启动SimGuardService进行检查
                    val serviceIntent = Intent(context, com.phonGuard.service.SimGuardService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}