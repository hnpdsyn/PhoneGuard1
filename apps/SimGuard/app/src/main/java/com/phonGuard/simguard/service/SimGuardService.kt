package com.phonGuard.simguard.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.phonGuard.simguard.SimGuardApp
import com.phonGuard.simguard.core.NotificationHelper
import com.phonGuard.simguard.core.SecurityLogger
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SIM卡防护服务 - 检测SIM卡状态变化，ICCID绑定比对
 * v1.1 新增：换卡后自动向紧急号码发送告警短信
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
            SimGuardApp.CHANNEL_SERVICE,
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
        val config = (application as SimGuardApp).configManager
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
        // 节流：服务为10秒轮询，换卡后"已更换"状态会持续命中，30分钟内只告警一次
        val now = System.currentTimeMillis()
        if (now - lastSwapAlertTime < SWAP_ALERT_INTERVAL_MS) return
        lastSwapAlertTime = now

        val config = (application as SimGuardApp).configManager
        val logger = (application as SimGuardApp).securityLogger

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
            SimGuardApp.CHANNEL_SIM_GUARD,
            "🚨 SIM卡已被更换！",
            "您的手机SIM卡已被更换，请立即检查手机安全！",
            SIM_ALERT_ID
        )

        // 向预设紧急号码发送告警短信（需已授权SEND_SMS权限）
        if (config.simAlertPhone.isNotEmpty()) {
            sendAlertSms(config.simAlertPhone)
        }
    }

    /**
     * v1.1 实装：换卡后用 SmsManager 发送告警短信。
     * 关键点：换卡后手机里插的是对方（入侵者）的SIM卡，
     * 告警短信经该卡发出，运营商账单/收件人即对方号码，
     * 机主由此获得追踪线索（经典 SIM Change Alert 设计，Avast/Lookout 同款思路）。
     */
    private fun sendAlertSms(phoneNumber: String) {
        val logger = (application as SimGuardApp).securityLogger
        scope.launch(Dispatchers.IO) {
            // 1. SEND_SMS 是危险权限，未授权时静默降级，只记日志不崩溃
            val granted = ContextCompat.checkSelfPermission(
                this@SimGuardService, Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = "sim",
                        subType = "sms_alert",
                        message = "未授权短信权限，换卡告警短信未发出（请在App内点击「授权发短信」）",
                        severity = 1
                    )
                )
                return@launch
            }

            try {
                val sm = obtainSmsManager()
                if (sm == null) {
                    logger.logEvent(
                        SecurityLogger.SecurityEvent(
                            type = "sim",
                            subType = "sms_alert",
                            message = "设备无短信管理器，告警短信发送失败",
                            severity = 1
                        )
                    )
                    return@launch
                }

                val body = buildAlertBody()
                val parts = sm.divideMessage(body)
                if (parts.size <= 1) {
                    sm.sendTextMessage(phoneNumber, null, body, null, null)
                } else {
                    // 超过单条长度自动拆分为多条
                    sm.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                }
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = "sim",
                        subType = "sms_alert",
                        message = "已向 $phoneNumber 提交换卡告警短信（经当前SIM卡发出）",
                        severity = 1
                    )
                )
            } catch (e: Exception) {
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = "sim",
                        subType = "sms_alert",
                        message = "告警短信发送失败：${e.message ?: "unknown"}",
                        severity = 1
                    )
                )
            }
        }
    }

    private fun obtainSmsManager(): SmsManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    } catch (e: Exception) {
        null
    }

    private fun buildAlertBody(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return "【PhoneGuard】您的手机SIM卡已被更换！设备：${Build.MODEL}，时间：$time。" +
                "若非本人操作，手机可能已被盗，请立即远程锁定、挂失SIM卡并报警。"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1004
        private const val SIM_ALERT_ID = 5001
        private const val SWAP_ALERT_INTERVAL_MS = 30 * 60 * 1000L
        private var lastSwapAlertTime = 0L
    }
}
