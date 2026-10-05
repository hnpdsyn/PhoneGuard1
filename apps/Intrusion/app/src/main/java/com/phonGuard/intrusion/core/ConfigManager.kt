package com.phonGuard.intrusion.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理中心（入侵检测精简版）
 * 仅保留：入侵检测开关、失败次数阈值、拍照取证、GPS定位、警报音
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("intrusion_prefs", Context.MODE_PRIVATE)

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

    // ========== 入侵短信告警（v1.1 新增） ==========
    var smsAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_ALERT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMS_ALERT_ENABLED, value).apply()

    var smsAlertPhone: String
        get() = prefs.getString(KEY_SMS_ALERT_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SMS_ALERT_PHONE, value).apply()

    // Key常量
    companion object {
        private const val KEY_INTRUSION_ENABLED = "intrusion_enabled"
        private const val KEY_INTRUSION_THRESHOLD = "intrusion_threshold"
        private const val KEY_INTRUSION_PHOTO = "intrusion_photo"
        private const val KEY_INTRUSION_LOCATION = "intrusion_location"
        private const val KEY_INTRUSION_ALARM = "intrusion_alarm"
        private const val KEY_SMS_ALERT_ENABLED = "sms_alert_enabled"
        private const val KEY_SMS_ALERT_PHONE = "sms_alert_phone"
    }
}
