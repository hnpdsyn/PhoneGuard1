package com.phonGuard.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理中心 - 使用加密存储保护敏感配置
 */
class ConfigManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "phone_guard_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("phone_guard_prefs", Context.MODE_PRIVATE)

    // ========== 主密码 ==========
    var masterPin: String
        get() = securePrefs.getString(KEY_MASTER_PIN, "1234") ?: "1234"
        set(value) = securePrefs.edit().putString(KEY_MASTER_PIN, value).apply()

    var isPinSet: Boolean
        get() = prefs.getBoolean(KEY_PIN_SET, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_SET, value).apply()

    // ========== 应用锁 ==========
    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var appLockTimeoutSeconds: Int
        get() = prefs.getInt(KEY_APP_LOCK_TIMEOUT, 30)
        set(value) = prefs.edit().putInt(KEY_APP_LOCK_TIMEOUT, value).apply()

    var appLockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, value).apply()

    // ========== ADB防火墙 ==========
    var adbGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADB_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADB_GUARD_ENABLED, value).apply()

    var adbAutoDisable: Boolean
        get() = prefs.getBoolean(KEY_ADB_AUTO_DISABLE, true)
        set(value) = prefs.edit().putBoolean(KEY_ADB_AUTO_DISABLE, value).apply()

    var adbWhitelist: Set<String>
        get() = prefs.getStringSet(KEY_ADB_WHITELIST, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ADB_WHITELIST, value).apply()

    var adbTakePhoto: Boolean
        get() = prefs.getBoolean(KEY_ADB_TAKE_PHOTO, false)
        set(value) = prefs.edit().putBoolean(KEY_ADB_TAKE_PHOTO, value).apply()

    // ========== 入侵检测 ==========
    var intrusionEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUSION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRUSION_ENABLED, value).apply()

    var intrusionThreshold: Int
        get() = prefs.getInt(KEY_INTRUSION_THRESHOLD, 5)
        set(value) = prefs.edit().putInt(KEY_INTRUSION_THRESHOLD, value).apply()

    var intrusionTakePhoto: Boolean
        get() = prefs.getBoolean(KEY_INTRUSION_PHOTO, true)
        set(value) = prefs.edit().putBoolean(KEY_INTRUSION_PHOTO, value).apply()

    var intrusionTrackLocation: Boolean
        get() = prefs.getBoolean(KEY_INTRUSION_LOCATION, true)
        set(value) = prefs.edit().putBoolean(KEY_INTRUSION_LOCATION, value).apply()

    var intrusionPlayAlarm: Boolean
        get() = prefs.getBoolean(KEY_INTRUSION_ALARM, true)
        set(value) = prefs.edit().putBoolean(KEY_INTRUSION_ALARM, value).apply()

    // ========== SIM卡防护 ==========
    var simGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIM_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SIM_GUARD_ENABLED, value).apply()

    var simBoundIccid: String
        get() = securePrefs.getString(KEY_SIM_BOUND_ICCID, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_SIM_BOUND_ICCID, value).apply()

    var simAlertPhone: String
        get() = securePrefs.getString(KEY_SIM_ALERT_PHONE, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_SIM_ALERT_PHONE, value).apply()

    // ========== 登录防护 ==========
    var loginGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOGIN_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGIN_GUARD_ENABLED, value).apply()

    var loginMaxAttempts: Int
        get() = prefs.getInt(KEY_LOGIN_MAX_ATTEMPTS, 5)
        set(value) = prefs.edit().putInt(KEY_LOGIN_MAX_ATTEMPTS, value).apply()

    var loginLockoutMinutes: Int
        get() = prefs.getInt(KEY_LOGIN_LOCKOUT_MINUTES, 5)
        set(value) = prefs.edit().putInt(KEY_LOGIN_LOCKOUT_MINUTES, value).apply()

    // ========== 服务状态 ==========
    var allServicesEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALL_SERVICES, false)
        set(value) = prefs.edit().putBoolean(KEY_ALL_SERVICES, value).apply()

    // Key常量
    companion object {
        private const val KEY_MASTER_PIN = "master_pin"
        private const val KEY_PIN_SET = "pin_set"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_ADB_GUARD_ENABLED = "adb_guard_enabled"
        private const val KEY_ADB_AUTO_DISABLE = "adb_auto_disable"
        private const val KEY_ADB_WHITELIST = "adb_whitelist"
        private const val KEY_ADB_TAKE_PHOTO = "adb_take_photo"
        private const val KEY_INTRUSION_ENABLED = "intrusion_enabled"
        private const val KEY_INTRUSION_THRESHOLD = "intrusion_threshold"
        private const val KEY_INTRUSION_PHOTO = "intrusion_photo"
        private const val KEY_INTRUSION_LOCATION = "intrusion_location"
        private const val KEY_INTRUSION_ALARM = "intrusion_alarm"
        private const val KEY_SIM_GUARD_ENABLED = "sim_guard_enabled"
        private const val KEY_SIM_BOUND_ICCID = "sim_bound_iccid"
        private const val KEY_SIM_ALERT_PHONE = "sim_alert_phone"
        private const val KEY_LOGIN_GUARD_ENABLED = "login_guard_enabled"
        private const val KEY_LOGIN_MAX_ATTEMPTS = "login_max_attempts"
        private const val KEY_LOGIN_LOCKOUT_MINUTES = "login_lockout_minutes"
        private const val KEY_ALL_SERVICES = "all_services_enabled"
    }
}