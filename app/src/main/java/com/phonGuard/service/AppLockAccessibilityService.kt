package com.phonGuard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.phonGuard.PhoneGuardApp
import com.phonGuard.ui.LockVerifyActivity

/**
 * 应用锁无障碍服务 - 实时监听前台应用变化，锁定受保护应用
 * 相比 UsageStatsManager 轮询方式，无障碍服务更准确实时
 */
class AppLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val currentPackage = event.packageName?.toString() ?: return
        // 忽略自身
        if (currentPackage == packageName) return

        val config = (application as PhoneGuardApp).configManager
        if (!config.appLockEnabled) return

        // 检查是否在锁定列表中
        if (currentPackage in config.appLockedPackages) {
            // 如果刚刚解锁过，跳过锁定（冷却期）
            if (isRecentlyUnlocked(currentPackage)) return

            val intent = Intent(this, LockVerifyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("target_package", currentPackage)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}

    companion object {
        private val recentlyUnlocked = mutableMapOf<String, Long>()
        private const val UNLOCK_COOLDOWN_MS = 5000L // 5秒冷却期

        /** 标记某个应用已解锁，冷却期内不再锁定 */
        fun markUnlocked(packageName: String) {
            recentlyUnlocked[packageName] = System.currentTimeMillis()
        }

        private fun isRecentlyUnlocked(packageName: String): Boolean {
            val lastUnlock = recentlyUnlocked[packageName] ?: return false
            return (System.currentTimeMillis() - lastUnlock) < UNLOCK_COOLDOWN_MS
        }
    }
}