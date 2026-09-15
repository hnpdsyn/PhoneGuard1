package com.phonGuard.adbguard.ui.screens

import android.Manifest
import android.content.Intent
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
import com.phonGuard.adbguard.AdbGuardApp
import com.phonGuard.adbguard.core.PermissionManager
import com.phonGuard.adbguard.service.AdbGuardService

/**
 * ADB防火墙主界面（单功能）
 * 顶部功能状态卡 + 开关 + 设置项 + 测试拍照 + 日志/照片入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbGuardScreen(
    onOpenLog: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = AdbGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.adbGuardEnabled) }
    var autoDisable by remember { mutableStateOf(config.adbAutoDisable) }
    var takePhoto by remember { mutableStateOf(config.adbTakePhoto) }
    var wifiMonitor by remember { mutableStateOf(config.adbWifiMonitor) }
    var usbPlugAlert by remember { mutableStateOf(config.usbPlugAlertEnabled) }
    var wirelessAdbAlert by remember { mutableStateOf(config.wirelessAdbAlertEnabled) }
    var devOptionsAlert by remember { mutableStateOf(config.devOptionsAlertEnabled) }
    var watermarkEnabled by remember { mutableStateOf(config.watermarkEnabled) }
    var ttsAlert by remember { mutableStateOf(config.ttsAlertEnabled) }
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
                                Icons.Default.DeveloperMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "ADB防火墙",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.adbGuardEnabled = it
                                if (it) {
                                    context.startForegroundService(
                                        Intent(context, AdbGuardService::class.java)
                                    )
                                } else {
                                    context.stopService(
                                        Intent(context, AdbGuardService::class.java)
                                    )
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "状态：" + if (enabled) "已开启，正在监控ADB连接" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "监控ADB调试连接，防止通过USB/WiFi远程入侵手机",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onOpenLog) {
                            Icon(Icons.Default.ListAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("安全日志")
                        }
                        TextButton(onClick = onOpenPhotos) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("入侵照片")
                        }
                    }
                }
            }
        }

        // ===== 事件时间线入口 =====
        item {
            Card(onClick = onOpenTimeline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("事件时间线", fontWeight = FontWeight.Medium)
                        Text(
                            "按时间倒序查看 ADB / USB / 无线调试 / 拍照取证等所有安全事件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // ===== 设置项：自动提醒 =====
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
                                "ADB开启后发送通知告警，并提醒手动关闭",
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

        // ===== 设置项：WiFi IP监控 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WiFi IP监控", fontWeight = FontWeight.Medium)
                            Text(
                                "记录ADB开启时的WiFi IP，检测无线ADB风险",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = wifiMonitor,
                            onCheckedChange = {
                                wifiMonitor = it
                                config.adbWifiMonitor = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 设置项：USB 插入实时告警 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("USB插入实时告警", fontWeight = FontWeight.Medium)
                            Text(
                                "插入USB设备/数据线时立即通知告警（不等轮询），可联动拍照取证",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = usbPlugAlert,
                            onCheckedChange = {
                                usbPlugAlert = it
                                config.usbPlugAlertEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 设置项：无线调试告警（Android 11+） =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("无线调试告警（Android 11+）", fontWeight = FontWeight.Medium)
                            Text(
                                "检测到无线调试（ADB over WiFi）开启时告警，Android 10及以下自动跳过",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = wirelessAdbAlert,
                            onCheckedChange = {
                                wirelessAdbAlert = it
                                config.wirelessAdbAlertEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 设置项：开发者选项监控 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开发者选项开关监控", fontWeight = FontWeight.Medium)
                            Text(
                                "检测到开发者选项被开启时告警（其中包含USB调试等高危开关）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = devOptionsAlert,
                            onCheckedChange = {
                                devOptionsAlert = it
                                config.devOptionsAlertEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 设置项：授权白名单 =====
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

        // ===== 设置项：自动拍照取证 =====
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

        // ===== 设置项：拍照时间水印 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("拍照时间水印", fontWeight = FontWeight.Medium)
                            Text(
                                "取证照片叠加拍摄时间与 ADB ALERT 标记，自动连拍2张留档",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = watermarkEnabled,
                            onCheckedChange = {
                                watermarkEnabled = it
                                config.watermarkEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 设置项：TTS 语音告警 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TTS 语音告警", fontWeight = FontWeight.Medium)
                            Text(
                                "检测到 ADB/USB 异常时语音播报警告，TTS不可用时静默降级",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = ttsAlert,
                            onCheckedChange = {
                                ttsAlert = it
                                config.ttsAlertEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 测试拍照 =====
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

        // ===== 安全提示 =====
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
