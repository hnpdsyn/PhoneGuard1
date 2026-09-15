package com.phonGuard.applock.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理中心（应用锁精简版） - 使用加密存储保护主密码
 * 仅保留：主密码、锁定应用列表、解锁冷却时间、密码错误次数阈值
 */
class ConfigManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "app_lock_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    // ========== 主密码（加密存储） ==========
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

    // ========== 密码错误次数阈值 ==========
    var lockMaxAttempts: Int
        get() = prefs.getInt(KEY_LOCK_MAX_ATTEMPTS, 5)
        set(value) = prefs.edit().putInt(KEY_LOCK_MAX_ATTEMPTS, value).apply()

    // ========== 图标伪装（0=不伪装 1=计算器 2=记事本） ==========
    var disguiseMode: Int
        get() = prefs.getInt(KEY_DISGUISE_MODE, DISGUISE_NONE)
        set(value) = prefs.edit().putInt(KEY_DISGUISE_MODE, value).apply()

    // ========== 隐私保险箱 ==========
    var vaultEnabled: Boolean
        get() = prefs.getBoolean(KEY_VAULT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_ENABLED, value).apply()

    // ========== 假崩溃（输错密码达到阈值时假装应用崩溃） ==========
    var fakeCrashEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAKE_CRASH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FAKE_CRASH_ENABLED, value).apply()

    // Key常量
    companion object {
        private const val KEY_MASTER_PIN = "master_pin"
        private const val KEY_PIN_SET = "pin_set"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
        private const val KEY_LOCKED_PACKAGES = "locked_packages"
        private const val KEY_LOCK_MAX_ATTEMPTS = "lock_max_attempts"
        private const val KEY_DISGUISE_MODE = "disguise_mode"
        private const val KEY_VAULT_ENABLED = "vault_enabled"
        private const val KEY_FAKE_CRASH_ENABLED = "fake_crash_enabled"

        // 伪装模式常量
        const val DISGUISE_NONE = 0
        const val DISGUISE_CALCULATOR = 1
        const val DISGUISE_NOTES = 2
    }
}
