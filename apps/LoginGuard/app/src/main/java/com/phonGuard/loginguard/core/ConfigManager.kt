package com.phonGuard.loginguard.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理中心（v1.1）
 * 防护总开关、失败次数阈值（自动锁机）、拍照取证开关与阈值、TTS 语音告警开关
 * 注：v1.0 的"锁定时间（分钟）"固定值配置已移除，
 *     v1.1 改为指数退避：连续触发锁机时长 1→2→4→8→16 分钟（上限 60），见 BreachManager
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("login_guard_prefs", Context.MODE_PRIVATE)

    // ========== 登录防护 ==========
    var loginGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOGIN_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGIN_GUARD_ENABLED, value).apply()

    /** 失败次数阈值：连续失败达到此次数自动锁机（默认 5 次） */
    var loginMaxAttempts: Int
        get() = prefs.getInt(KEY_LOGIN_MAX_ATTEMPTS, 5)
        set(value) = prefs.edit().putInt(KEY_LOGIN_MAX_ATTEMPTS, value).apply()

    // ========== 拍照取证（v1.1 新增） ==========
    /** 拍照取证开关（默认开启） */
    var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_ENABLED, value).apply()

    /** 拍照阈值：连续失败达到此次数触发前置取证（默认 2 次） */
    var photoCaptureThreshold: Int
        get() = prefs.getInt(KEY_PHOTO_CAPTURE_THRESHOLD, 2)
        set(value) = prefs.edit().putInt(KEY_PHOTO_CAPTURE_THRESHOLD, value).apply()

    // ========== TTS 语音告警（v1.1 新增） ==========
    /** TTS 语音告警开关（默认开启）：自动锁机时语音播报警告 */
    var ttsAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ALERT_ENABLED, value).apply()

    // Key常量
    companion object {
        private const val KEY_LOGIN_GUARD_ENABLED = "login_guard_enabled"
        private const val KEY_LOGIN_MAX_ATTEMPTS = "login_max_attempts"
        private const val KEY_CAPTURE_ENABLED = "capture_enabled"
        private const val KEY_PHOTO_CAPTURE_THRESHOLD = "photo_capture_threshold"
        private const val KEY_TTS_ALERT_ENABLED = "tts_alert_enabled"
    }
}
