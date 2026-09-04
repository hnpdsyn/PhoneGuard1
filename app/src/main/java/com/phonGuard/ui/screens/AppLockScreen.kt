package com.phonGuard.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.phonGuard.service.AppLockService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen() {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var enabled by remember { mutableStateOf(config.appLockEnabled) }
    var lockedPackages by remember { mutableStateOf(config.appLockedPackages.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var timeout by remember { mutableStateOf(config.appLockTimeoutSeconds.toString()) }

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
        item {
            Text(
                "应用锁",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "为指定应用添加锁定保护，防止他人打开",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("启用应用锁", fontWeight = FontWeight.Medium)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        config.appLockEnabled = it
                        if (it) {
                            context.startForegroundService(
                                android.content.Intent(context, AppLockService::class.java)
                            )
                        } else {
                            context.stopService(
                                android.content.Intent(context, AppLockService::class.java)
                            )
                        }
                    }
                )
            }
        }

        item {
            // 无障碍服务检查（替代使用情况访问权限，更可靠）
            if (!PermissionManager.isAccessibilityServiceEnabled(context)) {
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

        item {
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