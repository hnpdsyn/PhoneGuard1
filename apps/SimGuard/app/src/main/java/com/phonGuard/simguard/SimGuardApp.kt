package com.phonGuard.simguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.simguard.core.ConfigManager
import com.phonGuard.simguard.core.SecurityLogger

class SimGuardApp : Application() {

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
                CHANNEL_SIM_GUARD, "SIM卡防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "SIM卡更换检测和告警" },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_SIM_GUARD = "sim_guard"
        const val CHANNEL_SERVICE = "simguard_service"

        lateinit var instance: SimGuardApp
            private set
    }
}
