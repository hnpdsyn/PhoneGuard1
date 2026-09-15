package com.phonGuard.loginguard.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.phonGuard.loginguard.LoginGuardApp
import com.phonGuard.loginguard.core.BreachManager
import com.phonGuard.loginguard.core.NotificationHelper

/**
 * 登录防护前台服务 - 保活入口 + 兼容旧接口
 *
 * v1.1 改造说明：
 * - v1.0 的 recordLoginAttempt/isLockedOut 接口没有任何调用方（空转），且失败记录存在
 *   内存 Map 中重启即丢。v1.1 真正的防护处置迁移到 GuardAdminReceiver.onPasswordFailed
 *   （系统级回调：监听锁屏密码输错），内部状态全部走 BreachManager 的 SharedPreferences
 *   持久化，重启不丢失。
 * - 本服务保留前台常驻通知（可视化"防护已开启"），并继续提供 recordLoginAttempt /
 *   isLockedOut 兼容接口（与锁屏密码输错共用同一套持久化处置逻辑）。
 */
class LoginGuardService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统回收后尽量重启，保持防护常驻
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            LoginGuardApp.CHANNEL_SERVICE,
            "登录防护已开启",
            "设备管理器监控锁屏密码输错，防暴力破解运行中"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 记录一次登录尝试（兼容接口，供外部调用）
     * 失败 → 与锁屏密码输错同一套持久化处置逻辑（计数 / 拍照取证 / 自动锁机 / 指数退避）
     * 成功 → 清零失败计数与锁定状态
     */
    fun recordLoginAttempt(source: String, success: Boolean) {
        try {
            val config = (application as LoginGuardApp).configManager
            if (!config.loginGuardEnabled) return
            if (success) {
                BreachManager.onPasswordSucceeded(this)
            } else {
                BreachManager.onPasswordFailed(this, source = source)
            }
        } catch (_: Exception) {
            // 静默降级
        }
    }

    /**
     * 检查是否处于锁定期（兼容接口）
     * v1.1 状态持久化到 SharedPreferences，进程重启后依然有效
     */
    fun isLockedOut(source: String): Boolean {
        return try {
            BreachManager.isLockedOut(this)
        } catch (_: Exception) {
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1005
    }
}
