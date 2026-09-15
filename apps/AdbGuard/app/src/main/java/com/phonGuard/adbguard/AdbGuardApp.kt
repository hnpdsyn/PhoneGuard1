package com.phonGuard.adbguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.adbguard.core.ConfigManager
import com.phonGuard.adbguard.core.SecurityLogger

class AdbGuardApp : Application() {

    lateinit var configManager: ConfigManager
        private set
    lateinit var securityLogger: SecurityLogger
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        configManager = ConfigManager(this)
        securityLogger = SecurityLogger(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                CHANNEL_ADB_GUARD, "ADB防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "ADB连接检测和告警" },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_ADB_GUARD = "adb_guard"
        const val CHANNEL_SERVICE = "adbguard_service"

        lateinit var instance: AdbGuardApp
            private set
    }
}
