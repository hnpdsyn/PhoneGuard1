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
import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.PermissionManager
import com.phonGuard.service.AdbGuardService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbGuardScreen() {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.adbGuardEnabled) }
    var autoDisable by remember { mutableStateOf(config.adbAutoDisable) }
    var takePhoto by remember { mutableStateOf(config.adbTakePhoto) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelist by remember { mutableStateOf(config.adbWhitelist.toList()) }
    var testPhotoStatus by remember { mutableStateOf<String?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            testPhotoStatus = "相机权限已获取，点击测试按钮拍照"
        } else {
            testPhotoStatus = "❌ 相机权限被拒绝，请在系统设置中授予"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "ADB防火墙",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "监控ADB调试连接，防止通过USB/WiFi远程入侵手机",
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
                        Text("ADB防火墙", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.adbGuardEnabled = it
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "开启后，当ADB调试被启用时会立即通知您。（需先在首页点「开启所有防护」启动服务）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
                            Text("检测到ADB时自动提醒", fontWeight = FontWeight.Medium)
                            Text(
                                "ADB开启后发送通知告警",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = autoDisable,
                            onCheckedChange = {
                                autoDisable = it
                                config.adbAutoDisable = it
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
                        Text("授权白名单", fontWeight = FontWeight.Medium)
                        TextButton(onClick = { showWhitelistDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("管理")
                        }
                    }
                    if (whitelist.isEmpty()) {
                        Text(
                            "无白名单设备，所有ADB连接都会触发告警",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        whitelist.forEach { device ->
                            Text(
                                "• $device",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                            Text("检测到ADB时自动拍照取证", fontWeight = FontWeight.Medium)
                            Text(
                                "使用前置摄像头拍摄入侵者照片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = takePhoto,
                            onCheckedChange = {
                                takePhoto = it
                                config.adbTakePhoto = it
                                if (it && !PermissionManager.hasCameraPermission(context)) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📸 测试拍照", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "测试前置摄像头能否正常拍照（需先开启ADB防火墙服务）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!PermissionManager.hasCameraPermission(context)) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                testPhotoStatus = "拍照中..."
                                context.startService(
                                    Intent(context, AdbGuardService::class.java).apply {
                                        action = AdbGuardService.ACTION_TEST_PHOTO
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("拍照测试")
                    }
                    if (testPhotoStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            testPhotoStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "安全提示",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ADB调试是远程攻击者入侵手机的常见途径。建议在「开发者选项」中关闭USB调试，\n" +
                                "仅开发时临时开启。无线ADB调试风险更高，请谨慎使用。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showWhitelistDialog) {
        var newDevice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = { Text("管理白名单") },
            text = {
                Column {
                    whitelist.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(device, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                whitelist = whitelist - device
                                config.adbWhitelist = whitelist.toSet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "移除")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newDevice,
                            onValueChange = { newDevice = it },
                            placeholder = { Text("设备名称或IP") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            if (newDevice.isNotBlank()) {
                                whitelist = whitelist + newDevice.trim()
                                config.adbWhitelist = whitelist.toSet()
                                newDevice = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhitelistDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}