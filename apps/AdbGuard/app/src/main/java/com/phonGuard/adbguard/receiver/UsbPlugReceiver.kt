package com.phonGuard.adbguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonGuard.adbguard.core.UsbAlertHelper

/**
 * USB 插拔接收器（Manifest 静态注册兜底通道）
 *
 * ACTION_USB_DEVICE_ATTACHED / DETACHED 为系统受保护广播（仅系统可发送），
 * exported=true 无伪造风险。与 AdbGuardService 内运行时注册的接收器
 * 共用 UsbAlertHelper 统一处理（内部防抖去重），保证服务未存活时也能实时响应。
 */
class UsbPlugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            UsbAlertHelper.handleUsbEvent(
                context.applicationContext,
                intent.action,
                UsbAlertHelper.deviceNameOf(intent)
            )
        } catch (_: Exception) { }
    }
}
