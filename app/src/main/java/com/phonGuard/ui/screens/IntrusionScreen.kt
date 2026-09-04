package com.phonGuard.ui.screens

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
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.PermissionManager
import com.phonGuard.service.IntrusionDetectorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntrusionScreen() {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.intrusionEnabled) }
    var threshold by remember { mutableStateOf(config.intrusionThreshold.toString()) }
    var takePhoto by remember { mutableStateOf(config.intrusionTakePhoto) }
    var trackLocation by remember { mutableStateOf(config.intrusionTrackLocation) }
    var playAlarm by remember { mutableStateOf(config.intrusionPlayAlarm) }
    var testStatus by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        testStatus = if (granted) "相机权限已获取" else "❌ 相机权限被拒绝"
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        testStatus = if (allGranted) "位置权限已获取" else "❌ 位置权限被拒绝"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "入侵检测",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "检测异常解锁行为，自动拍照取证并定位",
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
                        Text("入侵检测", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.intrusionEnabled = it
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = threshold,
                onValueChange = {
                    threshold = it.filter { c -> c.isDigit() }
                    config.intrusionThreshold = threshold.toIntOrNull() ?: 5
                },
                label = { Text("触发阈值（连续失败次数）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("达到此次数后触发防护措施，建议3-10次") }
            )
        }

        item {
            Text("触发动作", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 拍照
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("前置摄像头拍照取证", fontWeight = FontWeight.Medium)
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
                                config.intrusionTakePhoto = it
                                if (it && !PermissionManager.hasCameraPermission(context)) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 定位
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("获取位置信息", fontWeight = FontWeight.Medium)
                            Text(
                                "记录入侵时的GPS位置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = trackLocation,
                            onCheckedChange = {
                                trackLocation = it
                                config.intrusionTrackLocation = it
                                if (it && !PermissionManager.hasLocationPermission(context)) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 警报
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("播放警报音+震动", fontWeight = FontWeight.Medium)
                            Text(
                                "发出警报声和强烈震动",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = playAlarm,
                            onCheckedChange = {
                                playAlarm = it
                                config.intrusionPlayAlarm = it
                            }
                        )
                    }
                }
            }
        }

        // 测试按钮
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚨 入侵测试", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "模拟入侵检测触发（需先开启入侵检测服务）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!PermissionManager.hasCameraPermission(context) && takePhoto) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                testStatus = "🚨 触发入侵检测..."
                                // 设置阈值为1，保证一次就触发
                                val oldThreshold = config.intrusionThreshold
                                config.intrusionThreshold = 1
                                context.startService(
                                    Intent(context, IntrusionDetectorService::class.java).apply {
                                        action = IntrusionDetectorService.ACTION_TEST_INTRUSION
                                    }
                                )
                                // 恢复阈值
                                config.intrusionThreshold = oldThreshold
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("模拟入侵检测")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            testStatus = null
                            context.startService(
                                Intent(context, IntrusionDetectorService::class.java).apply {
                                    action = IntrusionDetectorService.ACTION_RESET_COUNT
                                }
                            )
                        }
                    ) {
                        Text("重置计数", style = MaterialTheme.typography.bodySmall)
                    }
                    if (testStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            testStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // 权限状态
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("权限状态", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    val cameraOk = PermissionManager.hasCameraPermission(context)
                    val locationOk = PermissionManager.hasLocationPermission(context)
                    PermissionRow("📷 摄像头", cameraOk)
                    PermissionRow("📍 位置", locationOk)
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
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "注意事项",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 拍照需要摄像头权限\n" +
                                "2. 定位需要位置权限\n" +
                                "3. 由于Android系统限制，无法精确获取锁屏密码失败次数\n" +
                                "4. 入侵检测通过分析屏幕亮起/解锁模式来推断异常行为\n" +
                                "5. 可使用「模拟入侵检测」按钮测试功能是否正常",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            if (granted) "✓ 已授权" else "✗ 未授权",
            color = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}