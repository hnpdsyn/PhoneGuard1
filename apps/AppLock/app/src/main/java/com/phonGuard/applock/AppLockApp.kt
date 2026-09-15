package com.phonGuard.applock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.applock.core.ConfigManager

class AppLockApp : Application() {

    lateinit var configManager: ConfigManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        configManager = ConfigManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                CHANNEL_APP_LOCK, "应用锁", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "应用锁验证和锁定通知" },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_APP_LOCK = "app_lock"
        const val CHANNEL_SERVICE = "applock_service"

        lateinit var instance: AppLockApp
            private set
    }
}
