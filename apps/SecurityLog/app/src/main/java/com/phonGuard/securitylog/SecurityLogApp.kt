package com.phonGuard.securitylog

import android.app.Application
import com.phonGuard.securitylog.core.ConfigManager
import com.phonGuard.securitylog.core.SecurityLogger

class SecurityLogApp : Application() {

    lateinit var configManager: ConfigManager
        private set
    lateinit var securityLogger: SecurityLogger
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        configManager = ConfigManager(this)
        securityLogger = SecurityLogger(this)
    }

    companion object {
        lateinit var instance: SecurityLogApp
            private set
    }
}
