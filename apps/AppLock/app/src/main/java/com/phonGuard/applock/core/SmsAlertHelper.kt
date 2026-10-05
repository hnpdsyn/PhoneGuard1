package com.phonGuard.applock.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 入侵短信告警（v1.2 新增）
 * 密码输错触发取证时向紧急号码发送告警短信；内部 10 分钟节流，未授权 SEND_SMS 时静默跳过
 */
object SmsAlertHelper {

    private var lastSmsAt = 0L
    private const val SMS_INTERVAL_MS = 10 * 60 * 1000L

    /**
     * @param phone 紧急号码
     * @param body 短信内容（超长自动拆分）
     * @param onResult 结果回调（在发送线程执行），调用方可用于写日志
     */
    fun sendIfNeeded(context: Context, phone: String, body: String, onResult: (String) -> Unit = {}) {
        if (phone.isBlank()) return
        // SEND_SMS 为危险权限，未授权时静默跳过
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onResult("未授权短信权限，入侵告警短信未发出（请在App内授权）")
            return
        }
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
                val parts = sm.divideMessage(body)
                if (parts.size <= 1) {
                    sm.sendTextMessage(phone, null, body, null, null)
                } else {
                    sm.sendMultipartTextMessage(phone, null, parts, null, null)
                }
                onResult("已向 $phone 提交入侵告警短信")
            } catch (e: Exception) {
                onResult("入侵告警短信发送失败：${e.message ?: "unknown"}")
            }
        }.start()
    }

    /** 生成当前时间串（短信正文用） */
    fun nowText(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
}
