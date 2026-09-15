package com.phonGuard.loginguard.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.phonGuard.loginguard.LoginGuardApp
import com.phonGuard.loginguard.core.BreachManager
import com.phonGuard.loginguard.core.GuardAdminReceiver
import com.phonGuard.loginguard.service.LoginGuardService

/**
 * 登录防护主界面（v1.1）
 * 防护总开关 + 设备管理器激活引导卡 + 防护设置（失败阈值/拍照/TTS）+ 时间线与取证照片入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginGuardScreen(
    onOpenLog: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    onOpenGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = LoginGuardApp.instance
    val config = app.configManager

    var enabled by remember { mutableStateOf(config.loginGuardEnabled) }
    var maxAttempts by remember { mutableStateOf(config.loginMaxAttempts.toString()) }
    var captureEnabled by remember { mutableStateOf(config.captureEnabled) }
    var photoThreshold by remember { mutableStateOf(config.photoCaptureThreshold.toString()) }
    var ttsEnabled by remember { mutableStateOf(config.ttsAlertEnabled) }

    // 设备管理器激活状态
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = GuardAdminReceiver.getComponentName(context)
    var isAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    // 攻击计数状态（展示用，来自持久化存储）
    var failedCount by remember { mutableStateOf(BreachManager.getFailedCount(context)) }
    var escalationLevel by remember { mutableStateOf(BreachManager.getEscalationLevel(context)) }

    // 从系统设备管理器激活页返回时自动刷新激活状态与计数
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAdminActive = dpm.isAdminActive(adminComponent)
                failedCount = BreachManager.getFailedCount(context)
                escalationLevel = BreachManager.getEscalationLevel(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ===== 顶部防护总开关卡 =====
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "登录保护",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.loginGuardEnabled = it
                                if (it) {
                                    context.startForegroundService(
                                        Intent(context, LoginGuardService::class.java)
                                    )
                                } else {
                                    context.stopService(
                                        Intent(context, LoginGuardService::class.java)
                                    )
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "状态：" + if (enabled) "已开启，设备管理器监控锁屏密码输错" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "防止暴力破解：连续输错自动锁机 + 前置拍照取证 + 语音告警",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 当前攻击计数（持久化状态，来自 BreachManager）
                    Text(
                        "当前连续失败 $failedCount 次 · 已连续触发锁机 $escalationLevel 次" +
                                "（下次锁定 ${BreachManager.lockoutMinutesForLevel(escalationLevel)} 分钟）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 事件入口：时间线 / 取证照片 / 安全日志
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onOpenTimeline,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("时间线")
                        }
                        TextButton(
                            onClick = onOpenGallery,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("取证照片")
                        }
                        TextButton(
                            onClick = onOpenLog,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ListAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("安全日志")
                        }
                    }
                }
            }
        }

        // ===== 设备管理器激活状态卡（v1.1 新增） =====
        item {
            AdminCard(
                isActive = isAdminActive,
                onActivate = {
                    // 跳转系统设备管理器激活页面
                    try {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "激活后可在检测到暴力破解（锁屏密码连续输错）时自动锁屏保护设备，仅申请强制锁屏权限"
                            )
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            )
        }

        // ===== 防护设置：失败次数阈值 =====
        item {
            OutlinedTextField(
                value = maxAttempts,
                onValueChange = {
                    maxAttempts = it.filter { c -> c.isDigit() }
                    config.loginMaxAttempts = (maxAttempts.toIntOrNull() ?: 5).coerceAtLeast(1)
                },
                label = { Text("失败次数阈值（自动锁机）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("连续输错达到此次数即自动锁屏，建议3-10次") }
            )
        }

        // ===== 防护设置：拍照取证开关（v1.1 新增） =====
        item {
            SwitchRow(
                title = "解锁失败拍照取证",
                subtitle = "连续输错达到拍照阈值时，前置摄像头静默拍照存证",
                checked = captureEnabled,
                onCheckedChange = {
                    captureEnabled = it
                    config.captureEnabled = it
                }
            )
        }

        // ===== 防护设置：拍照阈值 =====
        item {
            OutlinedTextField(
                value = photoThreshold,
                onValueChange = {
                    photoThreshold = it.filter { c -> c.isDigit() }
                    config.photoCaptureThreshold = (photoThreshold.toIntOrNull() ?: 2).coerceAtLeast(1)
                },
                label = { Text("拍照阈值（连续失败次数）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = captureEnabled,
                supportingText = { Text("默认2次，连续输错达到此次数后开始拍照取证") }
            )
        }

        // ===== 防护设置：TTS 语音告警（v1.1 新增） =====
        item {
            SwitchRow(
                title = "TTS 语音告警",
                subtitle = "触发自动锁机时语音播报「检测到暴力破解攻击，已自动锁机」",
                checked = ttsEnabled,
                onCheckedChange = {
                    ttsEnabled = it
                    config.ttsAlertEnabled = it
                }
            )
        }

        // ===== 防护机制说明 =====
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("防护机制", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• 通过设备管理器监听每次锁屏密码输错\n" +
                                "• 连续失败达到阈值后 lockNow() 自动锁机\n" +
                                "• 锁定时长指数退避：1→2→4→8→16 分钟（上限60分钟）\n" +
                                "• 达到拍照阈值后前置摄像头静默取证并存入私有目录\n" +
                                "• 成功解锁后失败计数与锁定状态自动清零\n" +
                                "• 全部事件记录在时间线中，重启后状态不丢失",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 设备管理器激活状态卡（v1.1 新增）
 * 未激活时展示引导按钮（跳转 ACTION_ADD_DEVICE_ADMIN），并提示功能降级
 */
@Composable
private fun AdminCard(isActive: Boolean, onActivate: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isActive) Icons.Default.VerifiedUser else Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isActive) "设备管理器已激活" else "设备管理器未激活",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isActive)
                    "已获得强制锁屏权限：检测到暴力破解时可立即锁屏保护设备"
                else
                    "未激活时功能降级：仍会记录密码输错与拍照取证，但无法自动锁屏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onActivate) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("激活设备管理器")
                }
            }
        }
    }
}

/** 通用设置行：标题 + 说明 + 开关 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
