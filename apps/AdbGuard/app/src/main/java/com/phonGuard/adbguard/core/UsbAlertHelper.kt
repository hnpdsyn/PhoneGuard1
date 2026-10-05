package com.phonGuard.adbguard.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.phonGuard.adbguard.AdbGuardApp
import com.phonGuard.adbguard.service.AdbGuardService
import kotlinx.coroutines.runBlocking

/**
 * USB 插拔事件统一处理器
 *
 * Manifest 静态注册的 UsbPlugReceiver 与 AdbGuardService 内运行时注册的接收器共用本入口，
 * 内部 2 秒防抖去重，保证：服务存活时实时响应（运行时通道），服务未存活时兜底（Manifest 通道），
 * 且同一广播不会被双通道重复处理。
 */
object UsbAlertHelper {

    /** USB 告警通知 ID */
    const val USB_ALERT_ID = 2004

    /** USB 插入后拉起服务拍照的启动 action */
    const val ACTION_USB_PHOTO = "com.phonGuard.adbguard.action.USB_PHOTO"

    private var lastAction = ""
    private var lastEventAt = 0L

    /** 从 Intent 中提取 USB 设备名称（兼容 API 33 前后取值方式） */
    @Suppress("DEPRECATION")
    fun deviceNameOf(intent: Intent): String {
        return try {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            device?.productName?.takeIf { it.isNotBlank() } ?: "未知设备"
        } catch (_: Exception) {
            "未知设备"
        }
    }

    /**
     * 处理 USB 插拔事件：立即高优先级告警（不等 5 秒轮询）+ 写入事件时间线
     *
     * @return true 表示本次为新事件（已产生告警），调用方可据此触发 TTS 等后续动作
     */
    fun handleUsbEvent(context: Context, action: String?, deviceName: String): Boolean {
        if (action != UsbManager.ACTION_USB_DEVICE_ATTACHED &&
            action != UsbManager.ACTION_USB_DEVICE_DETACHED
        ) return false

        // 防抖：双通道可能同时收到同一广播
        val now = System.currentTimeMillis()
        if (action == lastAction && now - lastEventAt < 2000) return false
        lastAction = action
        lastEventAt = now

        val app = context.applicationContext as AdbGuardApp
        val config = app.configManager
        val attached = action == UsbManager.ACTION_USB_DEVICE_ATTACHED
        val title = if (attached) "🔌 USB设备已接入" else "🔌 USB设备已断开"
        val message = if (attached)
            "检测到USB设备「$deviceName」接入，若非本人操作请立即检查！"
        else
            "USB设备「$deviceName」已断开连接"

        // 写入事件时间线（SecurityLogger）
        try {
            runBlocking {
                app.securityLogger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = "usb",
                        subType = if (attached) "attached" else "detached",
                        message = message,
                        severity = if (attached) 2 else 0
                    )
                )
            }
        } catch (_: Exception) { }

        // 高优先级告警通知
        if (config.usbPlugAlertEnabled) {
            NotificationHelper.sendAlertNotification(
                context,
                AdbGuardApp.CHANNEL_ADB_GUARD,
                title,
                message,
                USB_ALERT_ID
            )
        }

        // v1.2：插入时向紧急号码发送告警短信（内部自带开关/权限/节流判断）
        sendSmsAlertIfNeeded(context, deviceName, attached)

        // 插入时若开启自动拍照且防火墙在运行，拉起服务拍照取证（相机与前台状态由服务持有）
        if (attached && config.adbTakePhoto && config.adbGuardEnabled &&
            PermissionManager.hasCameraPermission(context)
        ) {
            try {
                context.startForegroundService(
                    Intent(context, AdbGuardService::class.java).apply {
                        this.action = ACTION_USB_PHOTO
                    }
                )
            } catch (_: Exception) { }
        }
        return true
    }

    /** v1.2：短信告警节流（USB插拔/充电线插拔频繁，10分钟内最多发1条） */
    private var lastSmsAt = 0L
    private const val SMS_INTERVAL_MS = 10 * 60 * 1000L

    /**
     * v1.2：USB设备接入时向紧急号码发送告警短信
     * 条件：开关开启 + 号码非空 + 已授权SEND_SMS + 仅ATTACHED事件 + 10分钟节流
     */
    private fun sendSmsAlertIfNeeded(context: Context, deviceName: String, attached: Boolean) {
        if (!attached) return
        val app = context.applicationContext as AdbGuardApp
        if (!app.configManager.smsAlertEnabled) return
        val phone = app.configManager.smsAlertPhone.trim()
        if (phone.isEmpty()) return
        // SEND_SMS 为危险权限，未授权时静默跳过
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val now = System.currentTimeMillis()
        if (now - lastSmsAt < SMS_INTERVAL_MS) return
        lastSmsAt = now

        Thread {
            try {
                val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java) ?: return@Thread
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val body = "【PhoneGuard】您的手机接入USB设备「$deviceName」，时间：$time。" +
                        "若非本人操作，设备可能正被ADB入侵，App已自动拍照取证，请立即检查手机。"
                val parts = sm.divideMessage(body)
                if (parts.size <= 1) {
                    sm.sendTextMessage(phone, null, body, null, null)
                } else {
                    sm.sendMultipartTextMessage(phone, null, parts, null, null)
                }
                try {
                    runBlocking {
                        app.securityLogger.logEvent(
                            SecurityLogger.SecurityEvent(
                                type = "usb",
                                subType = "sms_alert",
                                message = "已向 $phone 提交USB接入告警短信",
                                severity = 1
                            )
                        )
                    }
                } catch (_: Exception) { }
            } catch (_: Exception) { }
        }.start()
    }
}
