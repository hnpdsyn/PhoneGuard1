package com.phonGuard.simguard.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理中心（SIM卡防护精简版） - 使用加密存储保护绑定的SIM卡信息
 * 仅保留：SIM防护开关、绑定ICCID（加密）、告警短信接收号码（加密）
 */
class ConfigManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "sim_guard_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sim_guard_prefs", Context.MODE_PRIVATE)

    // ========== SIM卡防护 ==========
    var simGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIM_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SIM_GUARD_ENABLED, value).apply()

    // 绑定的SIM卡标识（加密存储）
    var simBoundIccid: String
        get() = securePrefs.getString(KEY_SIM_BOUND_ICCID, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_SIM_BOUND_ICCID, value).apply()

    // 告警短信接收号码（加密存储）
    var simAlertPhone: String
        get() = securePrefs.getString(KEY_SIM_ALERT_PHONE, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_SIM_ALERT_PHONE, value).apply()

    // Key常量
    companion object {
        private const val KEY_SIM_GUARD_ENABLED = "sim_guard_enabled"
        private const val KEY_SIM_BOUND_ICCID = "sim_bound_iccid"
        private const val KEY_SIM_ALERT_PHONE = "sim_alert_phone"
    }
}
