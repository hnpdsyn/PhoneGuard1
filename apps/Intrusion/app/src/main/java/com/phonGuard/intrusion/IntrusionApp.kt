package com.phonGuard.intrusion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.intrusion.core.ConfigManager
import com.phonGuard.intrusion.core.SecurityLogger

class IntrusionApp : Application() {

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
                CHANNEL_INTRUSION, "入侵检测", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "入侵检测告警"
                enableVibration(true)
                setSound(null, null) // 静默拍照时无声音
            },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_INTRUSION = "intrusion"
        const val CHANNEL_SERVICE = "intrusion_service"

        lateinit var instance: IntrusionApp
            private set
    }
}
