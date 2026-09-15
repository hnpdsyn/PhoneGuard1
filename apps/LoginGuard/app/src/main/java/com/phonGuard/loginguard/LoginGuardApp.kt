package com.phonGuard.loginguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.phonGuard.loginguard.core.ConfigManager
import com.phonGuard.loginguard.core.SecurityLogger

class LoginGuardApp : Application() {

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
                CHANNEL_LOGIN_GUARD, "登录防护", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "异常登录检测和防护" },
            NotificationChannel(
                CHANNEL_SERVICE, "后台服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台服务运行状态" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        const val CHANNEL_LOGIN_GUARD = "login_guard"
        const val CHANNEL_SERVICE = "loginguard_service"

        lateinit var instance: LoginGuardApp
            private set
    }
}
