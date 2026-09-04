package com.phonGuard.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.PermissionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var isServiceRunning by remember { mutableStateOf(config.allServicesEnabled) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 盾牌图标
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = if (isServiceRunning) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "手机防火墙",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (isServiceRunning) "全防护已开启" else "防护未开启",
            style = MaterialTheme.typography.titleMedium,
            color = if (isServiceRunning) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 开关按钮
        Button(
            onClick = {
                if (isServiceRunning) {
                    onStopService()
                } else {
                    onStartService()
                }
                isServiceRunning = !isServiceRunning
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.Default.PowerSettingsNew
                else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isServiceRunning) "关闭所有防护" else "开启所有防护",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 功能模块状态卡片
        Text(
            text = "防护模块状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ModuleStatusCard(
            icon = Icons.Default.Lock,
            title = "应用锁",
            subtitle = "保护敏感应用",
            isEnabled = config.appLockEnabled,
            isRunning = isServiceRunning
        )
        ModuleStatusCard(
            icon = Icons.Default.DeveloperMode,
            title = "ADB防火墙",
            subtitle = "防止远程调试入侵",
            isEnabled = config.adbGuardEnabled,
            isRunning = isServiceRunning
        )
        ModuleStatusCard(
            icon = Icons.Default.Warning,
            title = "入侵检测",
            subtitle = "检测异常解锁行为",
            isEnabled = config.intrusionEnabled,
            isRunning = isServiceRunning
        )
        ModuleStatusCard(
            icon = Icons.Default.SimCard,
            title = "SIM卡防护",
            subtitle = "换卡自动锁定",
            isEnabled = config.simGuardEnabled,
            isRunning = isServiceRunning
        )
        ModuleStatusCard(
            icon = Icons.Default.Security,
            title = "登录防护",
            subtitle = "防暴力破解攻击",
            isEnabled = config.loginGuardEnabled,
            isRunning = isServiceRunning
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 权限状态
        Text(
            text = "权限状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        val permissions = PermissionManager.getAllPermissionStatus(app)
        PermissionStatusCard(permissions)
    }
}

@Composable
private fun ModuleStatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    isRunning: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled && isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled && isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = if (isEnabled && isRunning) "● 运行中" else "○ 已关闭",
                color = if (isEnabled && isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(permissions: Map<String, Boolean>) {
    val labels = mapOf(
        "camera" to "摄像头",
        "location" to "位置信息",
        "phone_state" to "电话状态",
        "notification" to "通知权限",
        "usage_stats" to "使用情况访问",
        "overlay" to "悬浮窗权限",
        "ignore_battery" to "忽略电池优化",
        "sms" to "短信"
    )

    permissions.forEach { (key, granted) ->
        val label = labels[key] ?: key
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                if (granted) "✓ 已授权" else "✗ 未授权",
                color = if (granted) Color(0xFF4CAF50) else Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}