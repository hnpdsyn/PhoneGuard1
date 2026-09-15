package com.phonGuard.securitylog.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理中心（安全日志精简版）
 * 仅保留：日志保留天数、默认筛选严重度
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("security_log_prefs", Context.MODE_PRIVATE)

    // ========== 日志设置 ==========
    var logRetentionDays: Int
        get() = prefs.getInt(KEY_LOG_RETENTION_DAYS, 30)
        set(value) = prefs.edit().putInt(KEY_LOG_RETENTION_DAYS, value).apply()

    var defaultMinSeverity: Int
        get() = prefs.getInt(KEY_DEFAULT_MIN_SEVERITY, 0)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_MIN_SEVERITY, value).apply()

    // Key常量
    companion object {
        private const val KEY_LOG_RETENTION_DAYS = "log_retention_days"
        private const val KEY_DEFAULT_MIN_SEVERITY = "default_min_severity"
    }
}
