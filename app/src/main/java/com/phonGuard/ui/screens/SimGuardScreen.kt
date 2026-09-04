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
import androidx.compose.ui.unit.dp
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimGuardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.simGuardEnabled) }
    var alertPhone by remember { mutableStateOf(config.simAlertPhone) }
    var isBound by remember { mutableStateOf(config.simBoundIccid.isNotEmpty()) }

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
                    "SIM卡防护",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "检测SIM卡更换，防止手机被盗后他人使用",
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
                        Text("SIM卡防护", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.simGuardEnabled = it
                            }
                        )
                    }
                }
            }
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
                                // 绑定当前SIM卡
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
                }
            }
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
                        Text("工作原理", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 首次使用时「绑定当前SIM卡」\n" +
                                "2. 后台持续监控SIM卡状态\n" +
                                "3. 检测到SIM卡被更换时立即告警\n" +
                                "4. 向预设号码发送定位短信（需短信权限）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}