package com.phonGuard.adbguard.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理中心（ADB防护精简版）
 * 仅保留：ADB防护开关、自动提醒关闭、自动拍照取证、WiFi IP监控、授权白名单
 * v1.1 新增：USB插拔告警、无线调试告警、开发者选项监控、拍照水印、TTS语音告警
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("adb_guard_prefs", Context.MODE_PRIVATE)

    // ========== ADB防火墙 ==========
    var adbGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADB_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADB_GUARD_ENABLED, value).apply()

    var adbAutoDisable: Boolean
        get() = prefs.getBoolean(KEY_ADB_AUTO_DISABLE, true)
        set(value) = prefs.edit().putBoolean(KEY_ADB_AUTO_DISABLE, value).apply()

    var adbTakePhoto: Boolean
        get() = prefs.getBoolean(KEY_ADB_TAKE_PHOTO, false)
        set(value) = prefs.edit().putBoolean(KEY_ADB_TAKE_PHOTO, value).apply()

    // ========== WiFi IP监控（无线ADB风险检测） ==========
    var adbWifiMonitor: Boolean
        get() = prefs.getBoolean(KEY_ADB_WIFI_MONITOR, true)
        set(value) = prefs.edit().putBoolean(KEY_ADB_WIFI_MONITOR, value).apply()

    // ========== USB 插拔实时告警 ==========
    var usbPlugAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_USB_PLUG_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_USB_PLUG_ALERT_ENABLED, value).apply()

    // ========== 无线调试告警（Android 11+） ==========
    var wirelessAdbAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIRELESS_ADB_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WIRELESS_ADB_ALERT_ENABLED, value).apply()

    // ========== 开发者选项开关监控 ==========
    var devOptionsAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEV_OPTIONS_ALERT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_OPTIONS_ALERT_ENABLED, value).apply()

    // ========== 拍照取证水印 ==========
    var watermarkEnabled: Boolean
        get() = prefs.getBoolean(KEY_WATERMARK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WATERMARK_ENABLED, value).apply()

    // ========== TTS 语音告警 ==========
    var ttsAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ALERT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ALERT_ENABLED, value).apply()

    // ========== 授权白名单 ==========
    var adbWhitelist: Set<String>
        get() = prefs.getStringSet(KEY_ADB_WHITELIST, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ADB_WHITELIST, value).apply()

    // Key常量
    companion object {
        private const val KEY_ADB_GUARD_ENABLED = "adb_guard_enabled"
        private const val KEY_ADB_AUTO_DISABLE = "adb_auto_disable"
        private const val KEY_ADB_TAKE_PHOTO = "adb_take_photo"
        private const val KEY_ADB_WIFI_MONITOR = "adb_wifi_monitor"
        private const val KEY_ADB_WHITELIST = "adb_whitelist"
        private const val KEY_USB_PLUG_ALERT_ENABLED = "usb_plug_alert_enabled"
        private const val KEY_WIRELESS_ADB_ALERT_ENABLED = "wireless_adb_alert_enabled"
        private const val KEY_DEV_OPTIONS_ALERT_ENABLED = "dev_options_alert_enabled"
        private const val KEY_WATERMARK_ENABLED = "watermark_enabled"
        private const val KEY_TTS_ALERT_ENABLED = "tts_alert_enabled"
    }
}
