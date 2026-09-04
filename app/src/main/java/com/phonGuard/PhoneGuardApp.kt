package com.phonGuard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.core.ConfigManager
import com.phonGuard.core.SecurityLogger

class PhoneGuardApp : Application() {

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
                CHANNEL_APP_LOCK, "应用锁", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "应用锁验证和锁定通知" },
            NotificationChannel(
                CHANNEL_ADB_GUARD, "ADB防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "ADB连接检测和告警" },
            NotificationChannel(
                CHANNEL_INTRUSION, "入侵检测", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "入侵检测告警"
                enableVibration(true)
                setSound(null, null) // 静默拍照时无声音
            },
            NotificationChannel(
                CHANNEL_SIM_GUARD, "SIM卡防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "SIM卡更换检测和告警" },
            NotificationChannel(
                CHANNEL_LOGIN_GUARD, "登录防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "异常登录检测和防护" },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_APP_LOCK = "app_lock"
        const val CHANNEL_ADB_GUARD = "adb_guard"
        const val CHANNEL_INTRUSION = "intrusion"
        const val CHANNEL_SIM_GUARD = "sim_guard"
        const val CHANNEL_LOGIN_GUARD = "login_guard"
        const val CHANNEL_SERVICE = "phone_guard_service"

        lateinit var instance: PhoneGuardApp
            private set
    }
}