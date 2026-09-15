package com.phonGuard.loginguard.core

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 设备管理器接收器（v1.1 核心新增）
 *
 * v1.0 致命问题：LoginGuardService.recordLoginAttempt/isLockedOut 没有任何调用方（空转）。
 * v1.1 通过系统 DeviceAdminReceiver 回调拿到真实的锁屏密码输错事件，形成防护闭环：
 *
 * onPasswordFailed 处置链（在 BreachManager.onPasswordFailed 中执行）：
 *   1. 失败计数持久化到 SharedPreferences（修复 v1.0 内存 Map 重启丢失问题）
 *   2. 达到拍照阈值（默认 2 次）→ 前置摄像头静默拍照取证（子线程执行，Camera 在 finally 中 release）
 *   3. 达到失败阈值（默认 5 次）→ DevicePolicyManager.lockNow() 自动锁机
 *   4. 指数退避：连续触发锁机的时长 1→2→4→8→16 分钟（上限 60 分钟），成功解锁清零
 *   5. 全事件写入 SecurityLogger 时间线 + 告警通知 + TTS 语音播报
 *
 * 权限最小化：res/xml/device_admin.xml 仅申请 force-lock 策略。
 */
class GuardAdminReceiver : DeviceAdminReceiver() {

    /**
     * 锁屏密码输错（系统级回调）
     * 注：这里重写旧签名而非 API 34 新签名，因为 API 34 的新回调
     *     onPasswordFailed(Context, Intent, UserHandle, Int) 默认实现会委托到旧签名，
     *     重写旧签名即可同时覆盖 API 28~34+ 全部版本，兼容性最好。
     */
    @Suppress("DEPRECATION")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        try {
            // 委托给处置引擎：持久化计数 / 拍照取证 / lockNow 自动锁机 / 指数退避 / 告警
            BreachManager.onPasswordFailed(context)
        } catch (_: Throwable) {
            // 防护逻辑异常不允许影响系统回调，静默降级
        }
    }

    /**
     * 锁屏密码输入成功（正常解锁）
     * 清零失败计数、指数退避等级与锁定期，并记录"解锁成功"时间线事件
     */
    @Suppress("DEPRECATION")
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        try {
            BreachManager.onPasswordSucceeded(context)
        } catch (_: Throwable) {
            // 静默降级
        }
    }

    /** 设备管理器被激活 */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        try {
            Toast.makeText(context, "登录保护已激活，防暴力破解防护生效", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }

    /** 设备管理器被停用 */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        try {
            Toast.makeText(context, "设备管理器已停用，自动锁机防护失效", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) { }
    }

    companion object {
        /** 获取本接收器的 ComponentName（UI 判断激活状态 / 发起激活时使用） */
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, GuardAdminReceiver::class.java)
    }
}
