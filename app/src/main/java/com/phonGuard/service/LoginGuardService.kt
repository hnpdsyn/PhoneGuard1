package com.phonGuard.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.phonGuard.PhoneGuardApp
import com.phonGuard.R
import com.phonGuard.core.NotificationHelper
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.*

/**
 * 登录防护服务 - 防暴力破解、异常登录检测
 */
class LoginGuardService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val attemptRecords = mutableMapOf<String, MutableList<Long>>()
    private val lockoutRecords = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startCleanupRoutine()
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            PhoneGuardApp.CHANNEL_SERVICE,
            "登录防护已开启",
            "正在监控异常登录行为"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 记录一次登录尝试（供外部调用）
     */
    fun recordLoginAttempt(source: String, success: Boolean) {
        scope.launch {
            try {
                val config = (application as PhoneGuardApp).configManager
                if (!config.loginGuardEnabled) return@launch

                val now = System.currentTimeMillis()

                if (!success) {
                    // 记录失败尝试
                    val records = attemptRecords.getOrPut(source) { mutableListOf() }
                    records.add(now)

                    // 清理超过1小时的记录
                    records.removeAll { now - it > 3600000 }

                    // 检查是否达到阈值
                    val maxAttempts = config.loginMaxAttempts
                    if (records.size >= maxAttempts) {
                        handleBruteForceDetected(source, records.size)
                    }
                } else {
                    // 成功登录，清除该来源的失败记录
                    attemptRecords.remove(source)
                    lockoutRecords.remove(source)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * 检查是否被锁定
     */
    fun isLockedOut(source: String): Boolean {
        val lockoutUntil = lockoutRecords[source] ?: return false
        if (System.currentTimeMillis() < lockoutUntil) return true
        lockoutRecords.remove(source)
        return false
    }

    private suspend fun handleBruteForceDetected(source: String, attemptCount: Int) {
        val config = (application as PhoneGuardApp).configManager
        val logger = (application as PhoneGuardApp).securityLogger

        // 锁定该来源
        val lockoutMinutes = config.loginLockoutMinutes
        lockoutRecords[source] = System.currentTimeMillis() + (lockoutMinutes * 60 * 1000)

        logger.logEvent(
            SecurityLogger.SecurityEvent(
                type = "login",
                subType = "brute_force",
                message = "检测到暴力破解攻击！来源: $source，尝试次数: $attemptCount",
                severity = 2,
                details = org.json.JSONObject().apply {
                    put("source", source)
                    put("attempt_count", attemptCount)
                    put("lockout_minutes", lockoutMinutes)
                }
            )
        )

        // 发送告警通知
        NotificationHelper.sendAlertNotification(
            this,
            PhoneGuardApp.CHANNEL_LOGIN_GUARD,
            "🚨 暴力破解攻击检测！",
            "来源: $source\n已尝试 $attemptCount 次，已临时锁定 ${lockoutMinutes}分钟",
            LOGIN_ALERT_ID
        )
    }

    /**
     * 定期清理过期记录
     */
    private fun startCleanupRoutine() {
        scope.launch {
            while (isActive) {
                delay(60000) // 每分钟清理一次
                val now = System.currentTimeMillis()

                // 清理过期的锁定记录
                lockoutRecords.entries.removeAll { now >= it.value }

                // 清理过期的尝试记录（超过1小时）
                attemptRecords.values.forEach { records ->
                    records.removeAll { now - it > 3600000 }
                }
                attemptRecords.entries.removeAll { it.value.isEmpty() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1005
        private const val LOGIN_ALERT_ID = 6001
    }
}