package com.phonGuard.applock.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.applock.AppLockApp
import com.phonGuard.applock.core.ConfigManager
import com.phonGuard.applock.core.DisguiseManager
import com.phonGuard.applock.core.PermissionManager
import com.phonGuard.applock.service.AppLockService
import com.phonGuard.applock.vault.VaultActivity

/**
 * 应用锁主界面（单功能）
 * 顶部功能状态卡 + 开关 + 设置项 + 锁定应用列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen() {
    val context = LocalContext.current
    val app = AppLockApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.appLockEnabled) }
    var lockedPackages by remember { mutableStateOf(config.appLockedPackages.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var timeout by remember { mutableStateOf(config.appLockTimeoutSeconds.toString()) }
    var maxAttempts by remember { mutableStateOf(config.lockMaxAttempts.toString()) }
    var accessibilityOk by remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context)) }

    // 获取已安装的应用列表（带图标和名称）
    val installedApps = remember {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val activities = pm.queryIntentActivities(intent, 0)
        activities.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            AppInfo(packageName, appName, resolveInfo.loadIcon(pm))
        }.sortedBy { it.appName }
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
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "应用锁大师",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.appLockEnabled = it
                                if (it) {
                                    context.startForegroundService(
                                        Intent(context, AppLockService::class.java)
                                    )
                                } else {
                                    context.stopService(
                                        Intent(context, AppLockService::class.java)
                                    )
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "状态：" + if (enabled && accessibilityOk) "已开启，正在保护您的应用"
                        else if (enabled) "已开启，但无障碍服务未启用"
                        else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "为指定应用添加锁定保护，防止他人打开",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // ===== 无障碍服务检查 =====
        item {
            if (!accessibilityOk) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "需要开启「无障碍服务」才能正常使用应用锁",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }) {
                            Text("去开启")
                        }
                    }
                }
            }
        }

        // ===== 设置项 =====
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("设置", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = {
                            timeout = it.filter { c -> c.isDigit() }
                            config.appLockTimeoutSeconds = timeout.toIntOrNull() ?: 30
                        },
                        label = { Text("解锁后自动重新锁定（秒）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxAttempts,
                        onValueChange = {
                            maxAttempts = it.filter { c -> c.isDigit() }
                            config.lockMaxAttempts = maxAttempts.toIntOrNull() ?: 5
                        },
                        label = { Text("密码错误次数阈值") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("连续输错此次数后提示稍后再试，建议3-10次") }
                    )

                    // ===== 图标伪装 =====
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    var disguiseMode by remember { mutableStateOf(config.disguiseMode) }
                    Text("图标伪装", fontWeight = FontWeight.Medium)
                    Text(
                        "把桌面图标换成普通应用的样子",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        ConfigManager.DISGUISE_NONE to "不伪装",
                        ConfigManager.DISGUISE_CALCULATOR to "计算器",
                        ConfigManager.DISGUISE_NOTES to "备忘录"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = disguiseMode == mode,
                                    onClick = {
                                        if (disguiseMode != mode) {
                                            disguiseMode = mode
                                            DisguiseManager.applyDisguise(context, mode, config)
                                            Toast.makeText(
                                                context,
                                                "桌面图标将在几秒后刷新",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = disguiseMode == mode,
                                onClick = {
                                    if (disguiseMode != mode) {
                                        disguiseMode = mode
                                        DisguiseManager.applyDisguise(context, mode, config)
                                        Toast.makeText(
                                            context,
                                            "桌面图标将在几秒后刷新",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                            if (mode == ConfigManager.DISGUISE_NONE) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "（真实图标：应用锁大师）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // ===== 假崩溃 =====
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    var fakeCrashEnabled by remember { mutableStateOf(config.fakeCrashEnabled) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("假崩溃", fontWeight = FontWeight.Medium)
                            Text(
                                "输错密码时应用假装崩溃退出，入侵者不会察觉存在密码验证",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = fakeCrashEnabled,
                            onCheckedChange = {
                                fakeCrashEnabled = it
                                config.fakeCrashEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // ===== 隐私保险箱入口 =====
        item {
            Card(
                onClick = {
                    context.startActivity(Intent(context, VaultActivity::class.java))
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("隐私保险箱", fontWeight = FontWeight.Medium)
                        Text(
                            "存放私密图片和视频，需主密码访问",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "进入",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已锁定应用（${lockedPackages.size}）",
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加应用")
                }
            }
        }

        if (lockedPackages.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("尚未添加任何锁定应用")
                    }
                }
            }
        }

        items(lockedPackages) { pkg ->
            val appInfo = installedApps.find { it.packageName == pkg }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            appInfo?.appName ?: pkg,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = {
                        lockedPackages = lockedPackages - pkg
                        config.appLockedPackages = lockedPackages.toSet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // 添加应用对话框
    if (showAddDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredApps = if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("选择要锁定的应用") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索应用...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(filteredApps) { app ->
                            val isLocked = app.packageName in lockedPackages
                            Surface(
                                onClick = {
                                    if (!isLocked) {
                                        lockedPackages = lockedPackages + app.packageName
                                        config.appLockedPackages = lockedPackages.toSet()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, fontWeight = FontWeight.Medium)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    if (isLocked) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable
)
