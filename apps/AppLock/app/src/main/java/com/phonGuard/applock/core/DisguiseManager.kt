package com.phonGuard.applock.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 图标伪装管理器 - 通过 activity-alias 实现桌面图标切换
 *
 * 原理：
 * - Manifest 中注册三个互斥的 launcher alias：真实入口 MainAlias / 计算器 DisguiseCalcAlias / 备忘录 DisguiseNoteAlias
 * - 切换时先启用目标 alias，再禁用其余 alias，保证桌面任意时刻至少存在一个图标（避免双图标或无图标）
 *
 * 注意 ComponentName 构造：
 * - packageName 部分必须使用运行时包名（debug 构建带 .debug 后缀）
 * - className 部分必须使用 namespace 展开后的完整类名（与 applicationId 无关）
 */
object DisguiseManager {

    // Manifest 中注册的 alias 完整类名（基于 namespace com.phonGuard.applock 展开）
    private const val ALIAS_MAIN = "com.phonGuard.applock.MainAlias"
    private const val ALIAS_CALCULATOR = "com.phonGuard.applock.DisguiseCalcAlias"
    private const val ALIAS_NOTES = "com.phonGuard.applock.DisguiseNoteAlias"

    private val ALL_ALIASES = listOf(ALIAS_MAIN, ALIAS_CALCULATOR, ALIAS_NOTES)

    /**
     * 应用伪装模式
     * @param mode ConfigManager.DISGUISE_NONE / DISGUISE_CALCULATOR / DISGUISE_NOTES
     */
    fun applyDisguise(context: Context, mode: Int, config: ConfigManager) {
        val targetAlias = aliasForMode(mode)

        // 1. 先启用目标 alias（保证桌面始终有图标，切换原子性）
        setAliasEnabled(context, targetAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        // 2. 再禁用其余 alias（包括真实入口 alias 本身）
        ALL_ALIASES.filter { it != targetAlias }.forEach { alias ->
            setAliasEnabled(context, alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }

        // 3. 持久化模式
        config.disguiseMode = mode
    }

    /** 查询指定 alias 当前是否启用（用于恢复显示状态） */
    fun isAliasEnabled(context: Context, mode: Int): Boolean {
        val pm = context.packageManager
        return try {
            val state = pm.getComponentEnabledSetting(
                ComponentName(context.packageName, aliasForMode(mode))
            )
            when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> mode == ConfigManager.DISGUISE_NONE
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun aliasForMode(mode: Int): String = when (mode) {
        ConfigManager.DISGUISE_CALCULATOR -> ALIAS_CALCULATOR
        ConfigManager.DISGUISE_NOTES -> ALIAS_NOTES
        else -> ALIAS_MAIN
    }

    private fun setAliasEnabled(context: Context, alias: String, newState: Int) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, alias),
                newState,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // 个别定制 ROM 可能限制组件切换，静默失败避免影响主流程
        }
    }
}
