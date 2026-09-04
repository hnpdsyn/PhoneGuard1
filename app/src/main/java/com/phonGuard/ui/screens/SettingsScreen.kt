package com.phonGuard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var masterPin by remember { mutableStateOf(config.masterPin) }
    var isPinSet by remember { mutableStateOf(config.isPinSet) }
    var showPin by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 主密码
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("主密码", fontWeight = FontWeight.Medium)
                            Text(
                                if (isPinSet) "已设置" else "未设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPinSet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Button(onClick = { showSetPinDialog = true }) {
                            Text(if (isPinSet) "修改" else "设置")
                        }
                    }
                }
            }
        }

        // 权限管理
        item {
            Text("权限管理", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    PermissionItem(
                        title = "使用情况访问权限",
                        subtitle = "应用锁需要此权限来检测前台应用",
                        permissionKey = "usage_stats",
                        onGrant = { PermissionManager.openUsageStatsSettings(context) }
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionItem(
                        title = "悬浮窗权限",
                        subtitle = "应用锁验证弹窗需要此权限",
                        permissionKey = "overlay",
                        onGrant = { PermissionManager.openOverlaySettings(context) }
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    PermissionItem(
                        title = "忽略电池优化",
                        subtitle = "防止系统杀后台服务",
                        permissionKey = "ignore_battery",
                        onGrant = { PermissionManager.openBatteryOptimizationSettings(context) }
                    )
                }
            }
        }

        // 其他设置
        item {
            Text("其他", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开机自启动", fontWeight = FontWeight.Medium)
                            Text(
                                "手机重启后自动开启所有防护",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        // 开机自启动由BootReceiver自动处理
                        Text(
                            "已启用",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    // 设置密码对话框
    if (showSetPinDialog) {
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(if (isPinSet) "修改主密码" else "设置主密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it.filter { c -> c.isDigit() }.take(6)
                            error = ""
                        },
                        label = { Text("密码（4-6位数字）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPin) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    if (showPin) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter { c -> c.isDigit() }.take(6)
                            error = ""
                        },
                        label = { Text("确认密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (error.isNotEmpty()) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        newPin.length < 4 -> error = "密码至少4位数字"
                        newPin != confirmPin -> error = "两次密码不一致"
                        else -> {
                            config.masterPin = newPin
                            config.isPinSet = true
                            isPinSet = true
                            masterPin = newPin
                            showSetPinDialog = false
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    subtitle: String,
    permissionKey: String,
    onGrant: () -> Unit
) {
    val context = LocalContext.current
    val permissions = PermissionManager.getAllPermissionStatus(context)
    val granted = permissions[permissionKey] ?: false

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (granted) {
            Text("已授权", color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onGrant) {
                Text("去开启")
            }
        }
    }
}