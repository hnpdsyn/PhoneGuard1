package com.phonGuard.loginguard.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.phonGuard.loginguard.LoginGuardApp
import org.json.JSONObject

/**
 * 暴力破解处置引擎（v1.1 核心新增）
 *
 * 职责：
 * 1. 失败计数持久化到 SharedPreferences（修复 v1.0 内存 Map 重启丢失问题）
 * 2. 失败达到阈值（ConfigManager.loginMaxAttempts，默认 5 次）→ DevicePolicyManager.lockNow() 自动锁机
 * 3. 指数退避：锁定时长按"连续触发锁机的次数"指数递增 1→2→4→8→16 分钟（上限 60 分钟），
 *    成功解锁（onPasswordSucceeded）时全部清零
 * 4. 达到拍照阈值（默认 2 次）→ 前置摄像头静默取证（PhotoCapture，子线程执行）
 * 5. 解锁成功 / 密码输错 / 自动锁机 / 拍照取证全部写入 SecurityLogger 时间线
 * 6. 自动锁机时发送"🚨 检测到暴力破解，已自动锁机"告警通知 + TTS 语音播报
 */
object BreachManager {

    // ========== 持久化状态存储（进程重启后不丢失） ==========
    private const val PREFS_NAME = "login_guard_state"
    private const val KEY_FAILED_COUNT = "failed_count"           // 当前连续失败次数
    private const val KEY_ESCALATION_LEVEL = "escalation_level"   // 指数退避等级（连续触发锁机的次数）
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"         // 锁定期截止时间戳（毫秒）
    private const val KEY_LAST_FAILURE_TIME = "last_failure_time" // 最近一次失败时间戳
    private const val KEY_LAST_PHOTO_TIME = "last_photo_time"     // 最近一次拍照时间戳（拍照节流）

    /** 失败记录的过期窗口：两次输错间隔超过该值视为新一轮攻击，失败计数清零重新累计 */
    private const val STALE_WINDOW_MS = 30 * 60 * 1000L

    /** 拍照节流窗口：达到拍照阈值后，同一波攻击内最多每 60 秒拍一张，避免刷屏 */
    private const val PHOTO_THROTTLE_MS = 60 * 1000L

    /** 锁定时长上限（分钟） */
    private const val MAX_LOCKOUT_MINUTES = 60L

    /** 自动锁机告警通知 ID */
    private const val AUTO_LOCK_ALERT_ID = 6002

    private fun state(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== 状态读取（供 UI / 服务查询） ==========

    /** 当前连续失败次数 */
    fun getFailedCount(context: Context): Int = state(context).getInt(KEY_FAILED_COUNT, 0)

    /** 当前指数退避等级（连续触发锁机的次数） */
    fun getEscalationLevel(context: Context): Int = state(context).getInt(KEY_ESCALATION_LEVEL, 0)

    /** 是否处于锁定期内 */
    fun isLockedOut(context: Context): Boolean =
        System.currentTimeMillis() < state(context).getLong(KEY_LOCKOUT_UNTIL, 0L)

    /**
     * 根据连续触发次数计算本次锁定时长（分钟）
     * 第 1 次触发锁 1 分钟 → 之后 2 → 4 → 8 → 16 → ... 封顶 60 分钟
     */
    fun lockoutMinutesForLevel(level: Int): Long {
        var minutes = 1L
        repeat(level) { minutes *= 2 }
        return minutes.coerceAtMost(MAX_LOCKOUT_MINUTES)
    }

    // ========== 核心事件处理 ==========

    /**
     * 密码/登录输错一次（设备管理器系统回调与服务兼容接口共用入口）
     * @param systemFailedAttempts 系统回调携带的连续失败计数（服务接口调用时传 -1 表示无系统计数）
     * @param source 失败来源描述（设备管理器回调为"锁屏密码"，服务接口为外部传入来源）
     */
    fun onPasswordFailed(
        context: Context,
        systemFailedAttempts: Int = -1,
        source: String = "锁屏密码"
    ) {
        val app = context.applicationContext as? LoginGuardApp ?: return
        val config = app.configManager
        if (!config.loginGuardEnabled) return  // 防护总开关关闭时不处置

        val now = System.currentTimeMillis()
        val prefs = state(context)
        val logger = app.securityLogger

        // 读取持久化计数；距上次失败超过过期窗口则视为新一轮攻击，清零重新计数
        var failedCount = prefs.getInt(KEY_FAILED_COUNT, 0)
        val lastFailure = prefs.getLong(KEY_LAST_FAILURE_TIME, 0L)
        if (lastFailure > 0 && now - lastFailure > STALE_WINDOW_MS) {
            failedCount = 0
        }
        failedCount++

        // 持久化本次失败（重启不丢失，修复 v1.0 内存 Map 问题）
        prefs.edit()
            .putInt(KEY_FAILED_COUNT, failedCount)
            .putLong(KEY_LAST_FAILURE_TIME, now)
            .apply()

        // 事件时间线：密码输错
        val sourceLabel = if (source == "锁屏密码") "检测到锁屏密码输错" else "检测到登录失败（来源: $source）"
        val sysPart = if (systemFailedAttempts >= 0) "，系统计数 $systemFailedAttempts" else ""
        logger.logEvent(
            SecurityLogger.SecurityEvent(
                type = "login",
                subType = "password_failed",
                message = "$sourceLabel（连续第 $failedCount 次$sysPart）",
                severity = 1,
                details = JSONObject().apply {
                    put("failed_count", failedCount)
                    put("system_failed_attempts", systemFailedAttempts)
                    put("source", source)
                }
            )
        )

        // 达到拍照阈值 → 前置摄像头静默取证（子线程执行，内部自带节流与静默降级）
        if (config.captureEnabled && failedCount >= config.photoCaptureThreshold) {
            val lastPhoto = prefs.getLong(KEY_LAST_PHOTO_TIME, 0L)
            if (now - lastPhoto > PHOTO_THROTTLE_MS) {
                prefs.edit().putLong(KEY_LAST_PHOTO_TIME, now).apply()
                PhotoCapture.captureIntruderPhoto(context)
            }
        }

        // 达到失败次数阈值 → 自动锁机（lockNow + 告警通知 + TTS）
        if (failedCount >= config.loginMaxAttempts) {
            triggerAutoLock(context, failedCount)
        }
    }

    /**
     * 自动锁机：DevicePolicyManager.lockNow() 立即锁屏（需已激活设备管理器）
     * + "🚨 检测到暴力破解，已自动锁机"告警通知 + TTS 语音播报 + 时间线记录
     * 锁定时长按连续触发次数指数递增（1→2→4→8→16 分钟，上限 60 分钟）
     */
    private fun triggerAutoLock(context: Context, failedCount: Int) {
        val app = context.applicationContext as LoginGuardApp
        val config = app.configManager
        val prefs = state(context)
        val logger = app.securityLogger

        // 计算本次锁定时长并递增指数退避等级；本轮失败计数结束，下一轮重新累计
        val level = prefs.getInt(KEY_ESCALATION_LEVEL, 0)
        val lockoutMinutes = lockoutMinutesForLevel(level)
        prefs.edit()
            .putInt(KEY_ESCALATION_LEVEL, level + 1)
            .putInt(KEY_FAILED_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMinutes * 60 * 1000)
            .apply()

        // 尝试立即锁屏（需已激活设备管理器；未激活/异常时静默降级，仅告警）
        var locked = false
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, GuardAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                locked = true
            }
        } catch (_: Throwable) { }

        // 事件时间线：自动锁机
        logger.logEvent(
            SecurityLogger.SecurityEvent(
                type = "login",
                subType = "auto_lock",
                message = if (locked)
                    "🚨 检测到暴力破解（连续失败 $failedCount 次），已自动锁机 $lockoutMinutes 分钟"
                else
                    "🚨 检测到暴力破解（连续失败 $failedCount 次），设备管理器未激活，无法自动锁机",
                severity = 2,
                details = JSONObject().apply {
                    put("failed_count", failedCount)
                    put("lockout_minutes", lockoutMinutes)
                    put("escalation_level", level + 1)
                    put("locked", locked)
                }
            )
        )

        // 告警通知（点击跳转事件时间线）
        NotificationHelper.sendAlertNotification(
            context,
            LoginGuardApp.CHANNEL_LOGIN_GUARD,
            "🚨 检测到暴力破解，已自动锁机",
            if (locked) "连续失败 $failedCount 次，设备已锁定 $lockoutMinutes 分钟"
            else "连续失败 $failedCount 次，请激活设备管理器以启用自动锁机",
            AUTO_LOCK_ALERT_ID,
            "timeline"
        )

        // TTS 语音播报（开关开启时；失败静默降级不影响通知）
        if (config.ttsAlertEnabled) {
            TtsHelper.speak(context, "检测到暴力破解攻击，已自动锁机")
        }
    }

    /**
     * 密码/登录成功（正常解锁）：清零失败计数、指数退避等级与锁定期
     * 仅在存在未解除的攻击状态时记录"解锁成功"事件，避免每次正常解锁刷屏
     */
    fun onPasswordSucceeded(context: Context) {
        val app = context.applicationContext as? LoginGuardApp ?: return
        val prefs = state(context)
        val hadAttackState = prefs.getInt(KEY_FAILED_COUNT, 0) > 0 ||
                prefs.getInt(KEY_ESCALATION_LEVEL, 0) > 0

        // 指数退避与失败计数全部清零
        prefs.edit()
            .putInt(KEY_FAILED_COUNT, 0)
            .putInt(KEY_ESCALATION_LEVEL, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        if (hadAttackState) {
            app.securityLogger.logEvent(
                SecurityLogger.SecurityEvent(
                    type = "login",
                    subType = "unlock_success",
                    message = "解锁成功，失败计数与锁定状态已清零",
                    severity = 0,
                    details = JSONObject().apply { put("recovered", true) }
                )
            )
        }
    }
}
