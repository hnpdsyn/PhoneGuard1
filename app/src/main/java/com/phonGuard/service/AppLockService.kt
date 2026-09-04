package com.phonGuard.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.NotificationHelper

/**
 * 应用锁前台服务 - 保持后台进程存活
 * 实际锁定逻辑由 AppLockAccessibilityService（无障碍服务）处理
 */
class AppLockService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            PhoneGuardApp.CHANNEL_SERVICE,
            "应用锁已开启",
            "正在使用无障碍服务保护您的应用"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}