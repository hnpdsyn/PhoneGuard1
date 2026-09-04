package com.phonGuard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.PhoneGuardApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginGuardScreen(onBack: () -> Unit) {
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.loginGuardEnabled) }
    var maxAttempts by remember { mutableStateOf(config.loginMaxAttempts.toString()) }
    var lockoutMinutes by remember { mutableStateOf(config.loginLockoutMinutes.toString()) }

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
                    "登录防护",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "防止暴力破解攻击，自动锁定异常登录来源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                        Text("登录防护", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.loginGuardEnabled = it
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = maxAttempts,
                onValueChange = {
                    maxAttempts = it.filter { c -> c.isDigit() }
                    config.loginMaxAttempts = maxAttempts.toIntOrNull() ?: 5
                },
                label = { Text("最大允许失败次数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("达到此次数后临时锁定，建议3-10次") }
            )
        }

        item {
            OutlinedTextField(
                value = lockoutMinutes,
                onValueChange = {
                    lockoutMinutes = it.filter { c -> c.isDigit() }
                    config.loginLockoutMinutes = lockoutMinutes.toIntOrNull() ?: 5
                },
                label = { Text("锁定时间（分钟）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

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
                        "• 记录每次失败的登录尝试\n" +
                                "• 同一来源（IP/设备）超过阈值后自动锁定\n" +
                                "• 锁定期间所有来源的登录请求都会被拒绝\n" +
                                "• 锁定时间到期后自动解除\n" +
                                "• 成功登录后清除该来源的失败记录",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}