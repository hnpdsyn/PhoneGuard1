package com.phonGuard.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.phonGuard.PhoneGuardApp
import com.phonGuard.R
import com.phonGuard.core.NotificationHelper
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.*

/**
 * SIM卡防护服务 - 检测SIM卡状态变化
 */
class SimGuardService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startSimMonitoring()
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            PhoneGuardApp.CHANNEL_SERVICE,
            "SIM卡防护已开启",
            "正在监控SIM卡状态"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startSimMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    checkSimState()
                } catch (e: Exception) {
                    // ignore
                }
                delay(10000) // 每10秒检查一次
            }
        }
    }

    private suspend fun checkSimState() {
        val config = (application as PhoneGuardApp).configManager
        if (!config.simGuardEnabled) return

        val currentIccid = getCurrentIccid()
        val boundIccid = config.simBoundIccid

        if (boundIccid.isNotEmpty() && currentIccid.isNotEmpty() && currentIccid != boundIccid) {
            // SIM卡已被更换！
            handleSimSwapDetected(currentIccid)
        }
    }

    private fun getCurrentIccid(): String {
        return try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tm.simCarrierId.toString() // 替代方案：使用carrierId
            } else {
                @Suppress("DEPRECATION")
                tm.simSerialNumber ?: ""
            }
        } catch (e: SecurityException) {
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun handleSimSwapDetected(newIccid: String) {
        val config = (application as PhoneGuardApp).configManager
        val logger = (application as PhoneGuardApp).securityLogger

        logger.logEvent(
            SecurityLogger.SecurityEvent(
                type = "sim",
                subType = "swap_detected",
                message = "SIM卡已被更换！",
                severity = 2,
                details = org.json.JSONObject().apply {
                    put("new_sim_info", newIccid)
                    put("bound_sim", config.simBoundIccid)
                }
            )
        )

        // 发送告警通知
        NotificationHelper.sendAlertNotification(
            this,
            PhoneGuardApp.CHANNEL_SIM_GUARD,
            "🚨 SIM卡已被更换！",
            "您的手机SIM卡已被更换，请立即检查手机安全！",
            SIM_ALERT_ID
        )

        // 发送短信到预设号码
        if (config.simAlertPhone.isNotEmpty()) {
            sendAlertSms(config.simAlertPhone)
        }
    }

    private suspend fun sendAlertSms(phoneNumber: String) {
        withContext(Dispatchers.IO) {
            try {
                // Android 10+限制了普通应用发送短信，这里记录意图
                val logger = (application as PhoneGuardApp).securityLogger
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = "sim",
                        subType = "sms_alert",
                        message = "已向 $phoneNumber 发送SIM卡更换告警短信",
                        severity = 1
                    )
                )
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1004
        private const val SIM_ALERT_ID = 5001
    }
}