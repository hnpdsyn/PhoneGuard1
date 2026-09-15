package com.phonGuard.applock.core

import android.content.Context
import android.provider.Settings

/**
 * 权限管理工具（应用锁精简版） - 仅保留无障碍服务状态检查
 */
object PermissionManager {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/.service.AppLockAccessibilityService"
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
        } catch (e: Exception) {
            return false
        }
    }
}
