package com.phonGuard.securitylog.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.securitylog.SecurityLogApp
import com.phonGuard.securitylog.core.SecurityLogger
import kotlinx.coroutines.launch
import java.io.File

/**
 * 安全日志主界面 - 查看（今日/全部）/ 筛选（类型/严重度）/ 导出 / 清理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onShareExport: (File) -> Unit = {}) {
    val context = LocalContext.current
    val app = SecurityLogApp.instance
    val config = app.configManager
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf<List<SecurityLogger.SecurityEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }                 // 今日 / 全部
    var typeFilter by remember { mutableStateOf<String?>(null) }      // 类型筛选
    var minSeverity by remember { mutableStateOf(0) }                 // 严重度筛选 0/1/2
    var statusMsg by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        isLoading = true
        logs = if (showAll) app.securityLogger.getAllLogs() else app.securityLogger.getTodayLogs()
        isLoading = false
    }

    LaunchedEffect(showAll) {
        reload()
    }

    // 前端筛选结果
    val filtered = logs
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { it.severity >= minSeverity }

    Column(modifier = Modifier.fillMaxSize()) {
        // ===== 顶部功能状态卡/工具栏 =====
        TopAppBar(
            title = {
                Column {
                    Text("安全日志", fontWeight = FontWeight.Bold)
                    Text(
                        "共 ${filtered.size} 条（筛选后）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            actions = {
                // 导出
                IconButton(onClick = {
                    scope.launch {
                        statusMsg = "正在导出..."
                        val file = app.securityLogger.exportLogs(filtered)
                        statusMsg = if (file != null) {
                            onShareExport(file)
                            "已导出 ${filtered.size} 条：${file.name}"
                        } else "导出失败"
                    }
                }) {
                    Icon(Icons.Default.FileUpload, contentDescription = "导出日志")
                }
                // 清理旧日志
                IconButton(onClick = {
                    scope.launch {
                        val deleted = app.securityLogger.cleanOldLogs(config.logRetentionDays)
                        statusMsg = "已清理 $deleted 个超过 ${config.logRetentionDays} 天的日志文件"
                        reload()
                    }
                }) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "清理旧日志")
                }
                // 清空
                IconButton(onClick = {
                    scope.launch {
                        app.securityLogger.clearLogs()
                        statusMsg = "日志已清空"
                        reload()
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "清空日志")
                }
                // 刷新
                IconButton(onClick = {
                    scope.launch {
                        statusMsg = null
                        reload()
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        // ===== 筛选区 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // 今日/全部 + 严重度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showAll,
                    onClick = { showAll = false },
                    label = { Text("今日") }
                )
                FilterChip(
                    selected = showAll,
                    onClick = { showAll = true },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = minSeverity == 0,
                    onClick = { minSeverity = 0 },
                    label = { Text("全部级别") }
                )
                FilterChip(
                    selected = minSeverity == 1,
                    onClick = { minSeverity = 1 },
                    label = { Text("⚠ 警告+") }
                )
                FilterChip(
                    selected = minSeverity == 2,
                    onClick = { minSeverity = 2 },
                    label = { Text("🚨 严重") }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 类型筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { typeFilter = null },
                    label = { Text("全部类型") }
                )
                listOf(
                    "app_lock" to "应用锁",
                    "adb" to "ADB",
                    "intrusion" to "入侵",
                    "sim" to "SIM卡",
                    "login" to "登录",
                    "general" to "通用"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { typeFilter = if (typeFilter == type) null else type },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (statusMsg != null) {
            Text(
                statusMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ===== 日志列表 =====
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无符合条件的安全事件", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "一切正常，继续安心使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            app.securityLogger.logDemoEvent()
                            reload()
                        }
                    }) {
                        Text("写入一条演示事件")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.id }) { event ->
                    LogItem(event)
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LogItem(event: SecurityLogger.SecurityEvent) {
    val severityColor = when (event.severity) {
        2 -> MaterialTheme.colorScheme.error
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val typeLabel = when (event.type) {
        "app_lock" -> "应用锁"
        "adb" -> "ADB"
        "intrusion" -> "入侵检测"
        "sim" -> "SIM卡"
        "login" -> "登录防护"
        "general" -> "通用"
        else -> event.type
    }
    val severityLabel = when (event.severity) {
        2 -> "严重"
        1 -> "警告"
        else -> "信息"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.severity >= 2) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 严重度指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .padding(end = 0.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = severityColor,
                    shape = MaterialTheme.shapes.small
                ) {}
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "[$typeLabel] $severityLabel",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        event.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (event.subType.isNotEmpty()) {
                    Text(
                        "subType: ${event.subType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
