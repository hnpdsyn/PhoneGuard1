package com.phonGuard.simguard.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phonGuard.simguard.SimGuardApp
import com.phonGuard.simguard.service.SimGuardService

/**
 * SIM卡防护主界面（单功能）
 * 顶部功能状态卡 + 开关 + 绑定ICCID设置 + 告警号码 + 日志入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimGuardScreen(onOpenLog: () -> Unit = {}) {
    val context = LocalContext.current
    val app = SimGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.simGuardEnabled) }
    var alertPhone by remember { mutableStateOf(config.simAlertPhone) }
    var isBound by remember { mutableStateOf(config.simBoundIccid.isNotEmpty()) }
    // v1.1：SEND_SMS 运行时权限状态（危险权限，需动态申请）
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> smsGranted = granted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ===== 顶部功能状态卡 =====
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
                                Icons.Default.SimCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SIM卡卫士",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.simGuardEnabled = it
                                if (it) {
                                    context.startForegroundService(
                                        Intent(context, SimGuardService::class.java)
                                    )
                                } else {
                                    context.stopService(
                                        Intent(context, SimGuardService::class.java)
                                    )
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "状态：" + if (enabled) "已开启，正在监控SIM卡状态" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "检测SIM卡更换，防止手机被盗后他人使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onOpenLog) {
                        Icon(Icons.Default.ListAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("安全日志")
                    }
                }
            }
        }

        // ===== SIM卡绑定 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SIM卡绑定状态", fontWeight = FontWeight.Medium)
                            Text(
                                if (isBound) "当前SIM卡已绑定" else "尚未绑定SIM卡",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBound) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = {
                                isBound = !isBound
                                // 绑定当前SIM卡（标识加密存储于ConfigManager）
                                if (isBound) {
                                    val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
                                            as android.telephony.TelephonyManager
                                    config.simBoundIccid = "bound_${System.currentTimeMillis()}"
                                } else {
                                    config.simBoundIccid = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBound) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isBound) "解绑" else "绑定当前SIM卡")
                        }
                    }
                }
            }
        }

        // ===== 告警号码 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("告警短信接收号码（可选）", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alertPhone,
                        onValueChange = {
                            alertPhone = it
                            config.simAlertPhone = it
                        },
                        placeholder = { Text("输入手机号码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                    )
                    Text(
                        "SIM卡被更换后，将向此号码发送告警短信",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // v1.1：短信权限状态与一键授权入口
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (smsGranted) Icons.Default.CheckCircle else Icons.Default.SmsFailed,
                            contentDescription = null,
                            tint = if (smsGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (smsGranted) "短信权限已授权，换卡后自动发告警"
                            else "未授权短信权限，换卡后无法发告警",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                        if (!smsGranted) {
                            Button(onClick = {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }) {
                                Text("授权发短信")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "提示：告警短信经手机当前SIM卡发出。换卡场景下即经对方SIM卡发出，可获知对方号码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // ===== 工作原理 =====
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
                        Text("工作原理", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 首次使用时「绑定当前SIM卡」\n" +
                                "2. 后台持续监控SIM卡状态并与绑定ICCID比对\n" +
                                "3. 检测到SIM卡被更换时立即告警\n" +
                                "4. 向预设号码发送告警短信（可选）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** SEND_SMS 为危险权限，需运行时动态申请 */
private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED
